package com.pmplugin4j.manager;

import com.pmplugin4j.hotreload.PmPluginHotReloadManager;
import java.util.Collection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Loads discovered plugins and starts the roots selected for the active tenant. */
@Order(Ordered.LOWEST_PRECEDENCE)
public final class PmPluginBootstrap implements ApplicationListener<ContextRefreshedEvent> {

    private final ApplicationContext applicationContext;
    private final PmPluginManager pluginManager;
    private final TenantPluginSelector tenantPluginSelector;
    private final PmPluginHotReloadManager hotReloadManager;

    public PmPluginBootstrap(ApplicationContext applicationContext, PmPluginManager pluginManager,
            TenantPluginSelector tenantPluginSelector, PmPluginHotReloadManager hotReloadManager) {
        this.applicationContext = applicationContext;
        this.pluginManager = pluginManager;
        this.tenantPluginSelector = tenantPluginSelector;
        this.hotReloadManager = hotReloadManager;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        if (context.getParent() != null || context != applicationContext) {
            return;
        }
        if (pluginManager.isAutoStartPlugin()) {
            Collection<String> enabled = tenantPluginSelector.selectEnabledPlugins();
            pluginManager.loadPlugins();
            if (enabled.isEmpty() && pluginManager.getPluginProperties().getCurrentTenant() == null) {
                pluginManager.startPlugins();
            } else {
                pluginManager.startPlugins(enabled);
            }
        }
        pluginManager.setMainApplicationStarted(true);
        hotReloadManager.startWatching();
    }
}
