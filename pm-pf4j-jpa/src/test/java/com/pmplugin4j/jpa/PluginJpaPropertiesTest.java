package com.pmplugin4j.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

class PluginJpaPropertiesTest {

    @Test
    void usesFrameworkJpaConfigurationPrefix() {
        ConfigurationProperties annotation = PluginJpaProperties.class.getAnnotation(ConfigurationProperties.class);

        assertEquals("pm.pf4j.jpa", annotation.value());
    }
}
