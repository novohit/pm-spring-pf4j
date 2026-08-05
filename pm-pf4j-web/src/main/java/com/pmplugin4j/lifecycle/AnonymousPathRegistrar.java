package com.pmplugin4j.lifecycle;

import com.pmplugin4j.core.AnonymousPathEntry;
import com.pmplugin4j.core.AnonymousRouteDeclaration;
import com.pmplugin4j.core.PluginAnonymousPathRegistrar;
import com.pmplugin4j.webflux.PmRouterFunctions;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.server.RouterFunction;

/** Registers functional routes explicitly declared anonymous by plugins. */
public final class AnonymousPathRegistrar implements BuiltInPluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AnonymousPathRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH, PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        return 11;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginContext) {
        ApplicationContext hostContext = pluginContext.getParent();
        PluginAnonymousPathRegistrar registrar = hostContext.getBeanProvider(PluginAnonymousPathRegistrar.class)
            .getIfAvailable();
        if (registrar == null) {
            return;
        }
        Map<String, RouterFunction> routerFunctions = pluginContext.getBeansOfType(RouterFunction.class);
        for (RouterFunction routerFunction : routerFunctions.values()) {
            if (routerFunction instanceof PmRouterFunctions.AnnotatedRouterFunction annotated) {
                for (AnonymousRouteDeclaration declaration : annotated.getDeclarations()) {
                    if (declaration.reason() == null || declaration.reason().isBlank()) {
                        log.debug("[Plugin: {}] Skipping non-anonymous functional route {}:{}", pluginContext.getId(),
                                declaration.httpMethod(), declaration.pathPattern());
                        continue;
                    }
                    registrar.register(pluginContext.getId(),
                            new AnonymousPathEntry(pluginContext.getId(), declaration.pathPattern(),
                                    declaration.httpMethod(), null, null, declaration.reason(), LocalDateTime.now()));
                }
            }
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginContext) {
        ApplicationContext hostContext = pluginContext.getParent();
        PluginAnonymousPathRegistrar registrar = hostContext.getBeanProvider(PluginAnonymousPathRegistrar.class)
            .getIfAvailable();
        if (registrar != null) {
            registrar.unregister(pluginContext.getId());
        }
    }
}
