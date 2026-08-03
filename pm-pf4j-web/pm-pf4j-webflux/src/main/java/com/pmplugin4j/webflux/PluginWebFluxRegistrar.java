package com.pmplugin4j.webflux;

import com.pmplugin4j.core.AnonymousPathEntry;
import com.pmplugin4j.core.PluginAnonymousPathRegistrar;
import com.pmplugin4j.lifecycle.BuiltInPluginResourceRegistrar;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.security.PluginRouteRegistry;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/** Registers annotated and functional WebFlux routes owned by a plugin. */
public final class PluginWebFluxRegistrar implements BuiltInPluginResourceRegistrar {

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH, PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginContext) {
        ApplicationContext host = pluginContext.getParent();
        if (host == null) {
            return;
        }
        String pluginId = pluginContext.getId();
        host.getBeanProvider(PmPluginWebFluxRequestMappingHandlerMapping.class).ifAvailable(mapping -> {
            mapping.registerControllers(pluginId, pluginContext);
            publishRoutes(host, pluginId, mapping.getPluginPaths(pluginId),
                    mapping.getPluginAuthenticatedPaths(pluginId));
        });
        host.getBeanProvider(PmPluginWebFluxRouterFunctionRegistry.class)
            .ifAvailable(registry -> registerRouterFunctions(host, registry, pluginId, pluginContext));
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginContext) {
        ApplicationContext host = pluginContext.getParent();
        if (host == null) {
            return;
        }
        String pluginId = pluginContext.getId();
        host.getBeanProvider(PmPluginWebFluxRequestMappingHandlerMapping.class)
            .ifAvailable(mapping -> mapping.unregisterHandlerMethods(pluginId));
        host.getBeanProvider(PmPluginWebFluxRouterFunctionRegistry.class)
            .ifAvailable(registry -> registry.unregister(pluginId, routerFunctions(pluginContext)));
        host.getBeanProvider(PluginRouteRegistry.class).ifAvailable(registry -> registry.unregister(pluginId));
        host.getBeanProvider(PluginAnonymousPathRegistrar.class)
            .ifAvailable(registrar -> registrar.unregister(pluginId));
    }

    private void registerRouterFunctions(ApplicationContext host, PmPluginWebFluxRouterFunctionRegistry registry,
            String pluginId, AnnotationConfigApplicationContext pluginContext) {
        List<RouterFunction<ServerResponse>> functions = routerFunctions(pluginContext);
        registry.register(pluginId, functions);
        publishRoutes(host, pluginId, registry.getRouterFunctionPaths(pluginId),
                registry.getRouterFunctionAuthenticatedPaths(pluginId));
        PluginAnonymousPathRegistrar anonymous = host.getBeanProvider(PluginAnonymousPathRegistrar.class)
            .getIfAvailable();
        if (anonymous == null) {
            return;
        }
        for (RouterFunction<?> function : pluginContext.getBeansOfType(RouterFunction.class).values()) {
            if (function instanceof PmRouterFunctions.AnnotatedRouterFunction annotated) {
                annotated.getDeclarations()
                    .stream()
                    .filter(declaration -> !declaration.reason().isBlank())
                    .forEach(declaration -> anonymous.register(pluginId,
                            new AnonymousPathEntry(pluginId, declaration.pathPattern(), declaration.httpMethod(),
                                    function.getClass().getName(), "route", declaration.reason(),
                                    LocalDateTime.now())));
            }
        }
    }

    private void publishRoutes(ApplicationContext host, String pluginId, Map<String, Set<String>> routes,
            Map<String, Set<String>> authenticated) {
        host.getBeanProvider(PluginRouteRegistry.class)
            .ifAvailable(registry -> routes.forEach((method, paths) -> paths.forEach(path -> registry.register(pluginId,
                    method, path, authenticated.getOrDefault(method, Set.of()).contains(path)))));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<RouterFunction<ServerResponse>> routerFunctions(AnnotationConfigApplicationContext context) {
        List<RouterFunction<ServerResponse>> result = new ArrayList<>();
        for (RouterFunction function : context.getBeansOfType(RouterFunction.class).values()) {
            result.add(function instanceof PmRouterFunctions.AnnotatedRouterFunction annotated ? annotated.getDelegate()
                    : function);
        }
        return result;
    }
}
