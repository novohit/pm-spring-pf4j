package com.pmplugin4j.manager;

import java.util.Collection;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/** Loads discovered plugins and starts the roots selected for the active tenant. */
public final class PmPluginBootstrap {
    private final PmPluginManager pluginManager;
    private final TenantPluginSelector tenantPluginSelector;

    public PmPluginBootstrap(PmPluginManager pluginManager, TenantPluginSelector tenantPluginSelector) {
        this.pluginManager = pluginManager;
        this.tenantPluginSelector = tenantPluginSelector;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startPlugins() {
        Collection<String> enabled = tenantPluginSelector.selectEnabledPlugins();
        pluginManager.loadPlugins();
        pluginManager.startPlugins(enabled);
    }
}
