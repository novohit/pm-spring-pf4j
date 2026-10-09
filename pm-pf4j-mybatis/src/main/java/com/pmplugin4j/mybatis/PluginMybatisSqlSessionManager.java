package com.pmplugin4j.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/** Creates and tracks an independent MyBatis runtime for each plugin. */
public final class PluginMybatisSqlSessionManager {

    private static final Logger log = LoggerFactory.getLogger(PluginMybatisSqlSessionManager.class);

    private final ConcurrentHashMap<String, SqlSessionTemplate> pluginSessionCache = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final MybatisPlusInterceptor mybatisPlusInterceptor;
    private final MetaObjectHandler metaObjectHandler;

    /**
     * Shared across all plugins to avoid repeated DNS/network-interface probing that occurs when MyBatis-Plus
     * internally creates a {@code DefaultIdentifierGenerator} per {@code SqlSessionFactory}. Each probe may block for
     * seconds; with N plugins the cost is paid N times without this singleton.
     */
    private volatile IdentifierGenerator sharedIdentifierGenerator;

    public PluginMybatisSqlSessionManager(@NonNull DataSource dataSource,
            @NonNull MybatisPlusInterceptor mybatisPlusInterceptor, @Nullable MetaObjectHandler metaObjectHandler) {
        this.dataSource = dataSource;
        this.mybatisPlusInterceptor = mybatisPlusInterceptor;
        this.metaObjectHandler = metaObjectHandler;
        log.info("PluginMybatisSqlSessionManager initialized with shared DataSource: {}",
                dataSource.getClass().getSimpleName());
    }

    /**
     * Lazily create one shared {@link IdentifierGenerator} for all plugins. Uses UUID-hash-based workerId to avoid
     * DNS/network-interface probing, so the first plugin triggers the initialization once and all subsequent plugins
     * reuse the same instance.
     */
    private IdentifierGenerator resolveSharedIdentifierGenerator() {
        IdentifierGenerator local = sharedIdentifierGenerator;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (sharedIdentifierGenerator != null) {
                return sharedIdentifierGenerator;
            }
            int workerId = UUID.randomUUID().hashCode() & 31;
            int dataCenterId = 1;
            sharedIdentifierGenerator = new DefaultIdentifierGenerator(workerId, dataCenterId);
            log.info("Created shared IdentifierGenerator (workerId={}, dataCenterId={})", workerId, dataCenterId);
            return sharedIdentifierGenerator;
        }
    }

    public void initializeMyBatisForPlugin(String pluginId, String pluginBasePackage,
            GenericApplicationContext context) {

        String basePackage = getMapperPackage(pluginBasePackage);

        // 0. Avoid repeated initialization
        if (pluginSessionCache.containsKey(pluginId)) {
            log.warn("PluginMybatisSqlSessionManager already initialized for plugin '{}'. Skipping.", pluginId);
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting MyBatis initialization for plugin: '{}', scanning Mapper package: '{}'", pluginId,
                basePackage);

        try {
            // 1. Create SqlSessionFactory
            SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(pluginId);
            log.info("SqlSessionFactory created: {}", sqlSessionFactory);

            // 2. Create SqlSessionTemplate and cache it
            SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
            pluginSessionCache.put(pluginId, sqlSessionTemplate);
            log.info("SqlSessionTemplate created and cached for plugin: '{}'", pluginId);

            // 3. Get the BeanFactory of the plugin context
            DefaultListableBeanFactory beanFactory = context.getDefaultListableBeanFactory();

            // 4. Register the cached SqlSessionTemplate instance to the Spring container
            String sqlSessionBeanName = pluginId + "_sqlSessionTemplate";
            if (!beanFactory.containsBean(sqlSessionBeanName)) {
                beanFactory.registerSingleton(sqlSessionBeanName, sqlSessionTemplate);
                log.info("Registered cached SqlSessionTemplate as bean '{}' in plugin context", sqlSessionBeanName);
            } else {
                log.warn("SqlSessionTemplate bean '{}' already exists in plugin context, skipping registration",
                        sqlSessionBeanName);
            }

            // 5. Register TransactionManager for the plugin
            // The JPA registrar runs first. If JPA is active, its JpaTransactionManager uses this same bean name and
            // handles both JPA and MyBatis transactions because both runtimes share the host DataSource. Only register
            // a standalone DataSourceTransactionManager when no transaction manager already exists.
            // See com.pmplugin4j.jpa.PluginJpaRegistrar#order() and PluginMybatisRegistrar#order() for the ordering
            // contract that makes this detection reliable.
            String txManagerBeanName = pluginId + "_transactionManager";
            if (!beanFactory.containsBeanDefinition(txManagerBeanName)
                    && !beanFactory.containsSingleton(txManagerBeanName)) {
                BeanDefinitionBuilder tmBuilder = BeanDefinitionBuilder
                    .genericBeanDefinition(DataSourceTransactionManager.class);
                tmBuilder.addConstructorArgValue(dataSource);
                tmBuilder.setPrimary(true);
                beanFactory.registerBeanDefinition(txManagerBeanName, tmBuilder.getBeanDefinition());
                log.info("Registered DataSourceTransactionManager (@Primary) as bean '{}' for plugin [{}]",
                        txManagerBeanName, pluginId);
            } else {
                log.info("TransactionManager bean '{}' already exists for plugin [{}], skipping registration",
                        txManagerBeanName, pluginId);
            }

            // 6. Scan Mapper packages via MapperScannerConfigurer
            String scannerBeanName = pluginId + "_mapperScannerConfigurer";
            if (!beanFactory.containsBeanDefinition(scannerBeanName)) {
                BeanDefinitionBuilder scannerBuilder = BeanDefinitionBuilder
                    .genericBeanDefinition(MapperScannerConfigurer.class);
                scannerBuilder.addPropertyValue("basePackage", basePackage);
                scannerBuilder.addPropertyValue("sqlSessionTemplateBeanName", sqlSessionBeanName);
                beanFactory.registerBeanDefinition(scannerBeanName, scannerBuilder.getBeanDefinition());
                log.debug("Registered MapperScannerConfigurer as bean '{}' to scan package: {}", scannerBeanName,
                        basePackage);
            } else {
                log.warn("MapperScannerConfigurer bean '{}' already exists, skipping registration", scannerBeanName);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully initialized MyBatis for plugin: '{}' (took {} ms)", pluginId, duration);
        } catch (Exception exception) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to initialize MyBatis for plugin: '{}' (took {} ms). Cause: {}", pluginId, duration,
                    exception.getMessage(), exception);
            cleanupPluginResources(pluginId);
            throw new IllegalStateException(
                    "MyBatis initialization failed for plugin: " + pluginId + ": " + exception.getMessage(), exception);
        }
    }

    private static String getMapperPackage(String pluginBasePackage) {
        if (pluginBasePackage == null || pluginBasePackage.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin base package must not be null or empty");
        }
        return pluginBasePackage + ".db";
    }

    private SqlSessionFactory createSqlSessionFactory(String pluginId) throws Exception {
        log.info("Creating SqlSessionFactory for plugin: '{}'", pluginId);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        configuration.setLazyLoadingEnabled(false);
        log.info("MyBatis configuration: camelCase={}, cache={}, lazyLoading={}", true, false, false);

        MybatisSqlSessionFactoryBean factory = getMybatisSqlSessionFactoryBean(configuration, pluginId);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        log.trace("SqlSessionFactory built successfully for plugin: '{}'", pluginId);
        return sqlSessionFactory;
    }

    private MybatisSqlSessionFactoryBean getMybatisSqlSessionFactoryBean(MybatisConfiguration configuration,
            String pluginId) {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setPlugins(mybatisPlusInterceptor);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setIdentifierGenerator(resolveSharedIdentifierGenerator());
        // Each plugin owns its GlobalConfig, but shares the host's audit-field filling policy. A manually created
        // factory does not discover Spring MetaObjectHandler beans automatically, so wire the handler explicitly.
        // Do not share the host's entire GlobalConfig: it contains factory-specific mutable state.
        if (metaObjectHandler != null) {
            globalConfig.setMetaObjectHandler(metaObjectHandler);
        }
        factory.setGlobalConfig(globalConfig);
        factory.setTransactionFactory(new SpringManagedTransactionFactory());
        log.info("MyBatis factory configured with plugin: '{}'", pluginId);
        return factory;
    }

    public void cleanupPluginResources(String pluginId) {
        if (pluginId != null) {
            SqlSessionTemplate removed = pluginSessionCache.remove(pluginId);
            if (removed != null) {
                log.info("Cleaned up MyBatis resources for plugin: '{}'", pluginId);
            } else {
                log.debug("No cached SqlSessionTemplate found for plugin: '{}'", pluginId);
            }
        }
    }

    int trackedPluginCount() {
        return pluginSessionCache.size();
    }

    @PreDestroy
    public void destroy() {
        int count = pluginSessionCache.size();
        pluginSessionCache.clear();
        log.info("CloseOperation PluginMybatisSqlSessionManager. Cleared {} plugin session(s).", count);
    }
}
