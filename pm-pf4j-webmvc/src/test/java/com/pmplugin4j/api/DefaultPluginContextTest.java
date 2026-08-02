package com.pmplugin4j.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pmplugin4j.config.TenantPluginConfig;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class DefaultPluginContextTest {

    @Test
    void registersAndUnregistersPluginControllerMappings() throws Exception {
        RecordingHandlerMapping mappings = new RecordingHandlerMapping();
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
                AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext()) {
            host.getBeanFactory().registerSingleton("requestMappingHandlerMapping", mappings);
            host.refresh();
            plugin.setParent(host);
            plugin.refresh();
            DefaultPluginContext context = new DefaultPluginContext(
                    "com.example.plugin",
                    plugin,
                    new TenantPluginConfig.PluginInstanceConfig());
            SampleController controller = new SampleController();
            context.registerController(controller);
            assertEquals(1, mappings.registrations);

            context.unregisterController(controller);
            assertEquals(1, mappings.unregistrations);
        }
    }

    @RestController
    @RequestMapping("/sample")
    static class SampleController {
        @GetMapping("/hello")
        String hello() {
            return "hello";
        }
    }

    static final class RecordingHandlerMapping extends RequestMappingHandlerMapping {
        int registrations;
        int unregistrations;

        @Override
        public void registerMapping(
                RequestMappingInfo mapping, Object handler, Method method) {
            registrations++;
        }

        @Override
        public void unregisterMapping(RequestMappingInfo mapping) {
            unregistrations++;
        }
    }
}
