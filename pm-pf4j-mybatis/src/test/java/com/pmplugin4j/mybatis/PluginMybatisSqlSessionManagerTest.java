package com.pmplugin4j.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
            assertEquals("com.example.first.dao", scanner.getPropertyValues().get("basePackage"));
            assertEquals(2, manager.trackedPluginCount());
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
                new MybatisPlusInterceptor());
    }

    private static AnnotationConfigApplicationContext plugin(String pluginId) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId(pluginId);
        return context;
    }
}
