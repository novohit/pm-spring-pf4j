package com.pmplugin4j.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PluginPropertiesTest {

    @Test
    void bindsFromFrameworkConfigurationPrefix() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
            .addFirst(new MapPropertySource("test", Map.of("pm.pf4j.enabled", "true", "pm.pf4j.current-tenant", "local",
                    "pm.pf4j.directory", "build/plugins")));

        PluginProperties properties = Binder.get(environment)
            .bind("pm.pf4j", Bindable.of(PluginProperties.class))
            .orElseThrow(() -> new IllegalStateException("pm.pf4j configuration was not bound"));

        assertTrue(properties.isEnabled());
        assertEquals("local", properties.getCurrentTenant());
        assertEquals("build/plugins", properties.getDirectory());
    }
}
