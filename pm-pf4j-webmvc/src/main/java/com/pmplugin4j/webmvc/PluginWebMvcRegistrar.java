package com.pmplugin4j.webmvc;

import com.pmplugin4j.api.DefaultPluginContext;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.lifecycle.BuiltInPluginResourceRegistrar;
import java.util.Set;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Registers plugin controllers and OpenAPI resources after context refresh. */
public final class PluginWebMvcRegistrar implements BuiltInPluginResourceRegistrar {

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(
                PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginContext) {
        pluginContext.getBean(DefaultPluginContext.class).autoRegisterControllers();
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginContext) {
        pluginContext.getBean(DefaultPluginContext.class).unregisterAllControllers();
    }
}
