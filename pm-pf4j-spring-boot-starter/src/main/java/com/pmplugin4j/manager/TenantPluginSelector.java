package com.pmplugin4j.manager;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.config.TenantPluginConfig;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Selects plugin startup roots for the currently configured tenant. */
public final class TenantPluginSelector {
    private static final Logger log = LoggerFactory.getLogger(TenantPluginSelector.class);
    private final PluginProperties properties;

    public TenantPluginSelector(PluginProperties properties) {
        this.properties = properties;
    }

    public Collection<String> selectEnabledPlugins() {
        String tenantId = properties.getCurrentTenant();
        if (tenantId == null) {
            return Collections.emptyList();
        }
        TenantPluginConfig config = properties.getTenants().get(tenantId);
        if (config == null) {
            log.warn("No plugin config for tenant: {}", tenantId);
            return Collections.emptyList();
        }
        List<String> enabledPlugins = config.getEnabledPlugins();
        if (enabledPlugins != null && !enabledPlugins.isEmpty()) {
            return List.copyOf(enabledPlugins);
        }
        String profile = config.getProfile();
        if (profile == null || profile.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> profilePlugins = properties.getProfiles().get(profile);
        if (profilePlugins == null) {
            log.warn("Plugin profile '{}' not found for tenant '{}'", profile, tenantId);
            return Collections.emptyList();
        }
        return List.copyOf(profilePlugins);
    }
}
