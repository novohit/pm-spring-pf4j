package com.pmplugin4j.mybatis;

import com.pmplugin4j.lifecycle.BuiltInPluginResourceRegistrar;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Registers plugin-owned MyBatis resources in a plugin application context. */
public final class PluginMybatisRegistrar implements BuiltInPluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PluginMybatisRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        // Must run after PluginJpaRegistrar (order=20), so an existing plugin JpaTransactionManager can be detected
        // and reused instead of registering a competing DataSourceTransactionManager.
        return 30;
    }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginApplicationContext) {
        String pluginId = pluginApplicationContext.getId();
        String basePackage = pluginApplicationContext.getEnvironment().getRequiredProperty("pm.plugin.base-package");
        PluginMybatisSqlSessionManager manager = manager(pluginApplicationContext);
        if (manager == null) {
            log.debug("[{}] MyBatis session manager is not available, skipping mapper registration", pluginId);
            return;
        }
        manager.initializeMyBatisForPlugin(pluginId, basePackage, pluginApplicationContext);
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginApplicationContext) {
        PluginMybatisSqlSessionManager manager = manager(pluginApplicationContext);
        if (manager != null) {
            manager.cleanupPluginResources(pluginApplicationContext.getId());
        }
    }

    private static PluginMybatisSqlSessionManager manager(AnnotationConfigApplicationContext pluginApplicationContext) {
        ApplicationContext host = pluginApplicationContext.getParent();
        if (host == null) {
            return null;
        }
        return host.getBeanProvider(PluginMybatisSqlSessionManager.class).getIfAvailable();
    }
}
