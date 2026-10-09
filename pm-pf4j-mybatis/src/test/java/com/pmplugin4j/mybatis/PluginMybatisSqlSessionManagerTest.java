package com.pmplugin4j.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.pmplugin4j.mybatis.db.SampleRecordMapper;
import com.pmplugin4j.mybatis.model.SampleRecord;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class PluginMybatisSqlSessionManagerTest {

    @Test
    void createsIsolatedFactoriesTemplatesAndTransactionManagers() {
        PluginMybatisSqlSessionManager manager = manager();
        try (AnnotationConfigApplicationContext first = plugin("com.example.first");
                AnnotationConfigApplicationContext second = plugin("com.example.second")) {
            manager.initializeMyBatisForPlugin(first.getId(), "com.example.first", first);
            manager.initializeMyBatisForPlugin(second.getId(), "com.example.second", second);
            first.refresh();
            second.refresh();

            SqlSessionTemplate firstTemplate = first.getBean("com.example.first_sqlSessionTemplate",
                    SqlSessionTemplate.class);
            SqlSessionTemplate secondTemplate = second.getBean("com.example.second_sqlSessionTemplate",
                    SqlSessionTemplate.class);
            assertNotSame(firstTemplate, secondTemplate);
            assertNotSame(firstTemplate.getSqlSessionFactory(), secondTemplate.getSqlSessionFactory());

            BeanDefinition firstTransaction = first.getBeanFactory()
                .getBeanDefinition("com.example.first_transactionManager");
            assertEquals(DataSourceTransactionManager.class.getName(), firstTransaction.getBeanClassName());

            BeanDefinition scanner = first.getBeanFactory()
                .getBeanDefinition("com.example.first_mapperScannerConfigurer");
            assertEquals("com.example.first.db", scanner.getPropertyValues().get("basePackage"));
            assertEquals(2, manager.trackedPluginCount());
        }
    }

    @Test
    void executesBaseMapperOperationsAndParticipatesInPluginTransaction() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:plugin-mybatis;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        new JdbcTemplate(dataSource).execute("create table sample_record (id bigint primary key, name varchar(100))");
        PluginMybatisSqlSessionManager manager = new PluginMybatisSqlSessionManager(dataSource,
                new MybatisPlusInterceptor(), null);

        try (AnnotationConfigApplicationContext plugin = plugin("com.pmplugin4j.mybatis")) {
            manager.initializeMyBatisForPlugin(plugin.getId(), "com.pmplugin4j.mybatis", plugin);
            plugin.refresh();

            SampleRecordMapper mapper = plugin.getBean(SampleRecordMapper.class);
            DataSourceTransactionManager transactionManager = plugin
                .getBean("com.pmplugin4j.mybatis_transactionManager", DataSourceTransactionManager.class);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            transaction.executeWithoutResult(status -> {
                SampleRecord rolledBack = record(1L, "rolled-back");
                assertEquals(1, mapper.insert(rolledBack));
                status.setRollbackOnly();
            });
            assertNull(mapper.selectById(1L));

            SampleRecord committed = record(2L, "committed");
            assertEquals(1, mapper.insert(committed));
            assertEquals("committed", mapper.selectById(2L).getName());
        }
    }

    @Test
    void preservesTransactionManagerRegisteredByJpa() {
        PluginMybatisSqlSessionManager manager = manager();
        try (AnnotationConfigApplicationContext plugin = plugin("com.example.plugin")) {
            BeanDefinition existing = BeanDefinitionBuilder.genericBeanDefinition(Object.class).getBeanDefinition();
            plugin.registerBeanDefinition("com.example.plugin_transactionManager", existing);

            manager.initializeMyBatisForPlugin(plugin.getId(), "com.example.plugin", plugin);

            assertEquals(Object.class.getName(),
                    plugin.getBeanFactory()
                        .getBeanDefinition("com.example.plugin_transactionManager")
                        .getBeanClassName());
        }
    }

    @Test
    void cleanupRemovesTrackedPluginSession() {
        PluginMybatisSqlSessionManager manager = manager();
        try (AnnotationConfigApplicationContext plugin = plugin("com.example.plugin")) {
            manager.initializeMyBatisForPlugin(plugin.getId(), "com.example.plugin", plugin);
            assertEquals(1, manager.trackedPluginCount());

            manager.cleanupPluginResources(plugin.getId());

            assertEquals(0, manager.trackedPluginCount());
        }
    }

    @Test
    void duplicateInitializationIsIgnored() {
        PluginMybatisSqlSessionManager manager = manager();
        try (AnnotationConfigApplicationContext plugin = plugin("com.example.plugin")) {
            manager.initializeMyBatisForPlugin(plugin.getId(), "com.example.plugin", plugin);
            manager.initializeMyBatisForPlugin(plugin.getId(), "com.example.plugin", plugin);

            assertEquals(1, manager.trackedPluginCount());
            assertTrue(plugin.containsBean("com.example.plugin_sqlSessionTemplate"));
        }
    }

    private static PluginMybatisSqlSessionManager manager() {
        return new PluginMybatisSqlSessionManager(new DriverManagerDataSource("jdbc:test"),
                new MybatisPlusInterceptor(), null);
    }

    private static SampleRecord record(Long id, String name) {
        SampleRecord record = new SampleRecord();
        record.setId(id);
        record.setName(name);
        return record;
    }

    private static AnnotationConfigApplicationContext plugin(String pluginId) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId(pluginId);
        return context;
    }
}
