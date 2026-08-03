package com.pmplugin4j.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmplugin4j.config.TenantPluginConfig;
import com.pmplugin4j.core.PluginAuthenticated;
import com.pmplugin4j.security.PluginRouteRegistry;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
            DefaultPluginContext context = new DefaultPluginContext("com.example.plugin", plugin,
                    new TenantPluginConfig.PluginInstanceConfig());
            SampleController controller = new SampleController();
            context.registerController(controller);
            assertEquals(1, mappings.registrations);

            context.unregisterController(controller);
            assertEquals(1, mappings.unregistrations);
        }
    }

    @Test
    void publishesPluginAuthenticationRouteMetadata() {
        RecordingHandlerMapping mappings = new RecordingHandlerMapping();
        RecordingRouteRegistry routes = new RecordingRouteRegistry();
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
                AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext()) {
            host.getBeanFactory().registerSingleton("requestMappingHandlerMapping", mappings);
            host.getBeanFactory().registerSingleton("pluginRouteRegistry", routes);
            host.refresh();
            plugin.setParent(host);
            plugin.refresh();
            DefaultPluginContext context = new DefaultPluginContext("com.example.plugin", plugin,
                    new TenantPluginConfig.PluginInstanceConfig());
            context.registerController(new AuthenticatedController());
            assertTrue(routes.getPluginAuthenticatedPaths("com.example.plugin").get("GET").contains("/secure/hello"));
            context.unregisterAllControllers();
            assertTrue(routes.getPluginPaths("com.example.plugin").isEmpty());
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

    @RestController
    @RequestMapping("/secure")
    static class AuthenticatedController {
        @PluginAuthenticated
        @GetMapping("/hello")
        String hello() {
            return "hello";
        }
    }

    static final class RecordingRouteRegistry implements PluginRouteRegistry {
        private final Map<String, Map<String, Set<String>>> routes = new LinkedHashMap<>();
        private final Map<String, Map<String, Set<String>>> authenticated = new LinkedHashMap<>();

        @Override
        public void register(String pluginId, String httpMethod, String pathPattern, boolean pluginAuthenticated) {
            routes.computeIfAbsent(pluginId, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(httpMethod, ignored -> new LinkedHashSet<>())
                .add(pathPattern);
            if (pluginAuthenticated) {
                authenticated.computeIfAbsent(pluginId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(httpMethod, ignored -> new LinkedHashSet<>())
                    .add(pathPattern);
            }
        }

        @Override
        public Map<String, Set<String>> getPluginPaths(String pluginId) {
            return routes.getOrDefault(pluginId, Map.of());
        }

        @Override
        public Map<String, Set<String>> getPluginAuthenticatedPaths(String pluginId) {
            return authenticated.getOrDefault(pluginId, Map.of());
        }

        @Override
        public void unregister(String pluginId) {
            routes.remove(pluginId);
            authenticated.remove(pluginId);
        }
    }

    static final class RecordingHandlerMapping extends RequestMappingHandlerMapping {
        int registrations;
        int unregistrations;

        @Override
        public void registerMapping(RequestMappingInfo mapping, Object handler, Method method) {
            registrations++;
        }

        @Override
        public void unregisterMapping(RequestMappingInfo mapping) {
            unregistrations++;
        }
    }
}
