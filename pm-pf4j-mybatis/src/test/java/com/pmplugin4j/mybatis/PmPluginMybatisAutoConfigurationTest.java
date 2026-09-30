package com.pmplugin4j.mybatis;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PmPluginMybatisAutoConfigurationTest {

    @Test
    void exposesMybatisLifecycleRegistrarWhenModuleIsPresent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PmPluginMybatisAutoConfiguration.class)) {
            assertNotNull(context.getBean(PluginMybatisRegistrar.class));
        }
    }

    @Test
    void createsSessionManagerWhenHostProvidesDataSource() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("dataSource", new DriverManagerDataSource("jdbc:test"));
            context.register(PmPluginMybatisAutoConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(PluginMybatisSqlSessionManager.class));
        }
    }
}
