package com.pmplugin4j.jpa;

import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.lifecycle.BuiltInPluginResourceRegistrar;
import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Lifecycle adapter for optional per-plugin JPA infrastructure. */
public final class PluginJpaRegistrar implements BuiltInPluginResourceRegistrar {

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(
                PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginContext) {
        PluginJpaManager manager = manager(pluginContext);
        if (manager == null) {
            return;
        }
        String basePackage = pluginContext.getEnvironment().getProperty("pm.plugin.base-package");
        if (basePackage == null || basePackage.isBlank()) {
            throw new IllegalStateException("Missing pm.plugin.base-package for JPA initialization");
        }
        manager.initialize(pluginContext.getId(), basePackage + ".entity", pluginContext);
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginContext) {
        PluginJpaManager manager = manager(pluginContext);
        if (manager != null) {
            manager.cleanup(pluginContext.getId(), pluginContext);
        }
    }

    private static PluginJpaManager manager(AnnotationConfigApplicationContext pluginContext) {
        ApplicationContext host = pluginContext.getParent();
        if (host == null) {
            return null;
        }
        return host.getBeanProvider(PluginJpaManager.class).getIfAvailable();
    }
}
