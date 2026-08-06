package com.pmplugin4j.mybatis;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PmPluginMybatisAutoConfigurationTest {

    @Test
    void exposesMybatisLifecycleRegistrarWhenModuleIsPresent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PmPluginMybatisAutoConfiguration.class)) {
            assertNotNull(context.getBean(PluginMybatisRegistrar.class));
        }
    }
}
