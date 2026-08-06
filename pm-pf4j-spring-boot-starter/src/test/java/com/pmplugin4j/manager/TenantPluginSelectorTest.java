package com.pmplugin4j.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.config.TenantPluginConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantPluginSelectorTest {
    @Test
    void directPluginSelectionTakesPrecedenceOverProfile() {
        PluginProperties properties = properties();
        TenantPluginConfig config = new TenantPluginConfig();
        config.setEnabledPlugins(List.of("direct"));
        config.setProfile("shared");
        properties.getTenants().put("tenant", config);
        properties.getProfiles().put("shared", List.of("profile"));
        assertEquals(List.of("direct"), new TenantPluginSelector(properties).selectEnabledPlugins());
    }

    @Test
    void resolvesProfilesAndReturnsEmptyForUnknownTenant() {
        PluginProperties properties = properties();
        TenantPluginConfig config = new TenantPluginConfig();
        config.setProfile("shared");
        properties.getTenants().put("tenant", config);
        properties.getProfiles().put("shared", List.of("one", "two"));
        assertEquals(List.of("one", "two"), new TenantPluginSelector(properties).selectEnabledPlugins());
        properties.setCurrentTenant("missing");
        assertTrue(new TenantPluginSelector(properties).selectEnabledPlugins().isEmpty());
    }

    private PluginProperties properties() {
        PluginProperties properties = new PluginProperties();
        properties.setCurrentTenant("tenant");
        return properties;
    }
}
