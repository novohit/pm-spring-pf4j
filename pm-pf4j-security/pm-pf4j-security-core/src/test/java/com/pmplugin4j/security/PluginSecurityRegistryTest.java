package com.pmplugin4j.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmplugin4j.PmPluginAuthRegistry;
import com.pmplugin4j.core.AnonymousPathEntry;
import com.pmplugin4j.core.DefaultPluginAnonymousPathRegistry;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginSecurityRegistryTest {

    @Test
    void registersAndRemovesAllRouteKindsByPlugin() {
        DefaultPluginRouteRegistry routes = new DefaultPluginRouteRegistry();
        DefaultPluginAnonymousPathRegistry anonymous = new DefaultPluginAnonymousPathRegistry();
        DefaultPluginAuthenticatedPathRegistry authenticated = new DefaultPluginAuthenticatedPathRegistry();
        routes.register("sample", "GET", "/sample/**", true);
        anonymous.register("sample", new AnonymousPathEntry("sample", "/sample/callback", "POST", "Controller",
                "callback", "external callback", LocalDateTime.now()));
        authenticated.register("sample", "GET", "/sample/**");
        assertEquals(Map.of("GET", Set.of("/sample/**")), routes.getPluginPaths("sample"));
        assertTrue(anonymous.isAnonymous("/sample/callback", "POST"));
        assertTrue(authenticated.isPluginAuthenticated("GET", "/sample/42"));
        routes.unregister("sample");
        anonymous.unregister("sample");
        authenticated.unregister("sample");
        assertTrue(routes.getPluginPaths("sample").isEmpty());
        assertFalse(anonymous.isAnonymous("/sample/callback", "POST"));
        assertFalse(authenticated.isPluginAuthenticated("GET", "/sample/42"));
    }

    @Test
    void resolvesMostSpecificPluginRouteAndOrdersProviders() {
        DefaultPluginAuthRegistry registry = new DefaultPluginAuthRegistry();
        registry.registerRoutes("general", Map.of("GET", Set.of("/api/**")));
        registry.registerRoutes("specific", Map.of("GET", Set.of("/api/orders/**")));
        registry.registerProvider("specific", new LateProvider());
        registry.registerProvider("specific", new EarlyProvider());
        assertEquals("specific", registry.lookupPluginId("GET", "/api/orders/1"));
        assertEquals(2, registry.getProviders("specific").size());
        PmPluginAuthRegistry.ProviderInfo info = registry.listProviders().iterator().next();
        assertEquals(2, info.providerCount());
        registry.unregister("specific");
        assertEquals("general", registry.lookupPluginId("GET", "/api/orders/1"));
    }

    private static final class EarlyProvider implements IPluginAuthenticationProvider {
        @Override
        public org.springframework.security.core.Authentication authenticate(
                jakarta.servlet.http.HttpServletRequest request) {
            return null;
        }

        @Override
        public int getOrder() {
            return 100;
        }
    }

    private static final class LateProvider implements IPluginAuthenticationProvider {
        @Override
        public org.springframework.security.core.Authentication authenticate(
                jakarta.servlet.http.HttpServletRequest request) {
            return null;
        }

        @Override
        public int getOrder() {
            return 200;
        }
    }
}
