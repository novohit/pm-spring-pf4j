package com.pmplugin4j.webmvc;

import com.pmplugin4j.lifecycle.BuiltInPluginResourceRegistrar;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.openapi.PluginOpenApiConfig;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/** Registers and removes plugin controller metadata from the host OpenAPI integration. */
public final class PluginOpenApiRegistrar implements BuiltInPluginResourceRegistrar {

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH, PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        return 11;
    }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginContext) {
        Set<Object> controllers = new LinkedHashSet<>();
        controllers.addAll(pluginContext.getBeansWithAnnotation(Controller.class).values());
        controllers.addAll(pluginContext.getBeansWithAnnotation(RestController.class).values());
        if (!controllers.isEmpty()) {
            PluginOpenApiConfig.registerPluginOpenApi(pluginContext.getParent(), pluginContext.getId(), controllers);
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginContext) {
        PluginOpenApiConfig.unregisterPluginOpenApi(pluginContext.getParent(), pluginContext.getId());
    }
}
