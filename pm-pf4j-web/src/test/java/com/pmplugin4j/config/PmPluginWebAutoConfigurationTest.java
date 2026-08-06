package com.pmplugin4j.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.pmplugin4j.lifecycle.AnonymousPathRegistrar;
import com.pmplugin4j.lifecycle.ControllerRegistrar;
import com.pmplugin4j.webmvc.PluginOpenApiRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PmPluginWebAutoConfigurationTest {

    @Test
    void exposesWebLifecycleRegistrarsAsFrameworkBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PmPluginWebAutoConfiguration.class)) {
            assertNotNull(context.getBean(ControllerRegistrar.class));
            assertNotNull(context.getBean(AnonymousPathRegistrar.class));
            assertNotNull(context.getBean(PluginOpenApiRegistrar.class));
        }
    }
}
