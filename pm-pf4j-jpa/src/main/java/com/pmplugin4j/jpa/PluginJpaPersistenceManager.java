package com.pmplugin4j.jpa;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/** Creates and tracks one independent JPA persistence unit per plugin. */
public final class PluginJpaPersistenceManager {

    private static final Logger log = LoggerFactory.getLogger(PluginJpaPersistenceManager.class);

    private final Set<String> initializedPlugins = ConcurrentHashMap.newKeySet();
    private final DataSource dataSource;
    private final JpaVendorAdapter jpaVendorAdapter;
    private final PluginJpaProperties defaultProperties;
    private final PluginJpaRepositoryScanner repositoryScanner = new PluginJpaRepositoryScanner();

    public PluginJpaPersistenceManager(@NonNull DataSource dataSource, JpaVendorAdapter jpaVendorAdapter,
            PluginJpaProperties defaultProperties) {
        this.dataSource = dataSource;
        this.jpaVendorAdapter = jpaVendorAdapter;
        this.defaultProperties = defaultProperties;
        log.info("PluginJpaPersistenceManager initialized with shared DataSource: {}",
                dataSource.getClass().getSimpleName());
    }

    public void initializeJpaForPlugin(String pluginId, String pluginBasePackage, GenericApplicationContext context) {

        String entityPackage = pluginBasePackage + ".entity";
        String repositoryPackage = pluginBasePackage + ".repository";

        if (initializedPlugins.contains(pluginId)) {
            log.warn("JPA already initialized for plugin '{}'. Skipping.", pluginId);
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting JPA initialization for plugin: '{}', entity package: '{}', repository package: '{}'",
                pluginId, entityPackage, repositoryPackage);

        try {
            final DefaultListableBeanFactory beanFactory = context.getDefaultListableBeanFactory();

            // 1. Register EntityManagerFactory BeanDefinition
            final String emfBeanName = pluginId + "_entityManagerFactory";
            BeanDefinitionBuilder emfBuilder = BeanDefinitionBuilder
                .genericBeanDefinition(LocalContainerEntityManagerFactoryBean.class);
            emfBuilder.addPropertyValue("dataSource", dataSource);
            emfBuilder.addPropertyValue("packagesToScan", new String[]{entityPackage});
            emfBuilder.addPropertyValue("jpaVendorAdapter", jpaVendorAdapter);
            emfBuilder.addPropertyValue("jpaPropertyMap", defaultProperties.toJpaPropertyMap());
            emfBuilder.addPropertyValue("persistenceUnitName", pluginId);
            emfBuilder.setPrimary(true);
            beanFactory.registerBeanDefinition(emfBeanName, emfBuilder.getBeanDefinition());
            log.info("Registered EntityManagerFactory bean '{}' for plugin: '{}'", emfBeanName, pluginId);

            // 2. Register a transaction-aware shared EntityManager used by Spring Data repository factories
            String entityManagerBeanName = pluginId + "_sharedEntityManager";
            BeanDefinitionBuilder entityManagerBuilder = BeanDefinitionBuilder
                .genericBeanDefinition(SharedEntityManagerCreator.class);
            entityManagerBuilder.setFactoryMethod("createSharedEntityManager");
            entityManagerBuilder.addConstructorArgReference(emfBeanName);
            beanFactory.registerBeanDefinition(entityManagerBeanName, entityManagerBuilder.getBeanDefinition());

            // 3. Register JpaTransactionManager BeanDefinition (@Primary, replaces MyBatis
            // DataSourceTransactionManager)
            String txManagerBeanName = pluginId + "_transactionManager";
            BeanDefinitionBuilder tmBuilder = BeanDefinitionBuilder.genericBeanDefinition(JpaTransactionManager.class);
            tmBuilder.addPropertyReference("entityManagerFactory", emfBeanName);
            tmBuilder.setPrimary(true);
            beanFactory.registerBeanDefinition(txManagerBeanName, tmBuilder.getBeanDefinition());
            log.info("Registered JpaTransactionManager bean '{}' (@Primary) for plugin: '{}'", txManagerBeanName,
                    pluginId);

            // 4. Register PersistenceExceptionTranslationPostProcessor
            String etBeanName = pluginId + "_persistenceExceptionTranslator";
            if (!beanFactory.containsBeanDefinition(etBeanName) && !beanFactory.containsSingleton(etBeanName)) {
                BeanDefinitionBuilder exceptionTranslationBuilder = BeanDefinitionBuilder
                    .genericBeanDefinition(PersistenceExceptionTranslationPostProcessor.class);
                beanFactory.registerBeanDefinition(etBeanName, exceptionTranslationBuilder.getBeanDefinition());
                log.debug("Registered PersistenceExceptionTranslationPostProcessor for plugin: '{}'", pluginId);
            }

            // 5. Scan and register JPA repository beans
            List<Class<?>> repositoryInterfaces = repositoryScanner.scan(repositoryPackage, context.getClassLoader());
            for (Class<?> repositoryInterface : repositoryInterfaces) {
                String repositoryBeanName = generateRepositoryBeanName(pluginId, repositoryInterface);
                if (!beanFactory.containsBeanDefinition(repositoryBeanName)) {
                    BeanDefinition repositoryDefinition = repositoryScanner
                        .createRepositoryBeanDefinition(repositoryInterface, entityManagerBeanName, txManagerBeanName);
                    beanFactory.registerBeanDefinition(repositoryBeanName, repositoryDefinition);
                    log.debug("Registered JPA repository bean '{}' for interface: {}", repositoryBeanName,
                            repositoryInterface.getName());
                }
            }

            initializedPlugins.add(pluginId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully initialized JPA for plugin: '{}', registered {} repository beans (took {} ms)",
                    pluginId, repositoryInterfaces.size(), duration);
        } catch (Exception exception) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to initialize JPA for plugin: '{}' (took {} ms). Cause: {}", pluginId, duration,
                    exception.getMessage(), exception);
            throw new IllegalStateException("JPA initialization failed for plugin: " + pluginId, exception);
        }
    }

    private static String generateRepositoryBeanName(String pluginId, Class<?> repositoryInterface) {
        String shortName = repositoryInterface.getSimpleName();
        int lastDot = pluginId.lastIndexOf('.');
        String suffix = lastDot >= 0 ? pluginId.substring(lastDot + 1) : pluginId;
        return suffix + "." + Character.toLowerCase(shortName.charAt(0)) + shortName.substring(1);
    }

    public void cleanupPluginResources(String pluginId, ApplicationContext pluginContext) {
        initializedPlugins.remove(pluginId);

        String emfBeanName = pluginId + "_entityManagerFactory";
        try {
            if (pluginContext.containsBean(emfBeanName)) {
                EntityManagerFactory entityManagerFactory = pluginContext.getBean(emfBeanName,
                        EntityManagerFactory.class);
                if (entityManagerFactory.isOpen()) {
                    entityManagerFactory.close();
                    log.info("Closed EntityManagerFactory for plugin: '{}'", pluginId);
                }
            }
        } catch (Exception exception) {
            log.warn("Failed to close EntityManagerFactory for plugin: '{}': {}", pluginId, exception.getMessage());
        }
    }

    int trackedPluginCount() {
        return initializedPlugins.size();
    }

    @PreDestroy
    public void destroy() {
        int count = initializedPlugins.size();
        initializedPlugins.clear();
        log.info("PluginJpaPersistenceManager destroyed ({} plugin(s) tracked). "
                + "EMF cleanup handled by per-plugin context.close().", count);
    }
}
