package com.pmplugin4j.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

class PluginJpaPropertiesTest {

    @Test
    void usesFrameworkJpaConfigurationPrefix() {
        ConfigurationProperties annotation = PluginJpaProperties.class.getAnnotation(ConfigurationProperties.class);

        assertEquals("pm.pf4j.jpa", annotation.value());
    }

    @Test
    void mirrorsGjHibernateDefaultsAndAllowsOverrides() {
        PluginJpaProperties properties = new PluginJpaProperties();
        properties.getExtraProperties().put("hibernate.jdbc.batch_size", 50);

        assertEquals("none", properties.toJpaPropertyMap().get("hibernate.hbm2ddl.auto"));
        assertFalse((Boolean) properties.toJpaPropertyMap().get("hibernate.show_sql"));
        assertEquals(true, properties.toJpaPropertyMap().get("hibernate.format_sql"));
        assertEquals(false, properties.toJpaPropertyMap().get("hibernate.generate_statistics"));
        assertEquals("org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                properties.toJpaPropertyMap().get("hibernate.physical_naming_strategy"));
        assertEquals(50, properties.toJpaPropertyMap().get("hibernate.jdbc.batch_size"));
    }
}
