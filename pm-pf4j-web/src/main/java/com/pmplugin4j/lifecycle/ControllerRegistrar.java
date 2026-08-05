package com.pmplugin4j.lifecycle;

import com.pmplugin4j.PmPluginAuthRegistry;
import com.pmplugin4j.core.AnonymousRouteDeclaration;
import com.pmplugin4j.webflux.PmPluginWebFluxRequestMappingHandlerMapping;
import com.pmplugin4j.webflux.PmPluginWebFluxRouterFunctionRegistry;
import com.pmplugin4j.webflux.PmRouterFunctions;
import com.pmplugin4j.webmvc.PmPluginRequestMappingHandlerMapping;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/** Registers plugin controllers and functional routes for the active web stack. */
public final class ControllerRegistrar implements BuiltInPluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ControllerRegistrar.class);
    private static final List<String> ALL_METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH");

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
        String pluginId = pluginContext.getId();
        ApplicationContext hostContext = pluginContext.getParent();
        PmPluginAuthRegistry authRegistry = lookupAuthRegistry(hostContext);

        Map<String, PmPluginWebFluxRequestMappingHandlerMapping> webFluxMappings = hostContext
            .getBeansOfType(PmPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            PmPluginWebFluxRequestMappingHandlerMapping handlerMapping = webFluxMappings.values().iterator().next();
            handlerMapping.registerControllers(pluginId, pluginContext);
            registerWebFluxPluginRoutes(authRegistry, pluginId, handlerMapping);
            registerRouterFunctions(authRegistry, pluginId, pluginContext, hostContext);
            return;
        }

        Map<String, PmPluginRequestMappingHandlerMapping> mvcMappings = hostContext
            .getBeansOfType(PmPluginRequestMappingHandlerMapping.class);
        if (!mvcMappings.isEmpty()) {
            PmPluginRequestMappingHandlerMapping handlerMapping = mvcMappings.values().iterator().next();
            handlerMapping.registerControllers(pluginId, pluginContext);
            registerMvcPluginRoutes(authRegistry, pluginId, handlerMapping);
        } else {
            log.debug("[Plugin: {}] No HandlerMapping found (non-web application), skipping controller registration.",
                    pluginId);
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginContext) {
        String pluginId = pluginContext.getId();
        ApplicationContext hostContext = pluginContext.getParent();
        Map<String, PmPluginWebFluxRequestMappingHandlerMapping> webFluxMappings = hostContext
            .getBeansOfType(PmPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            webFluxMappings.values().iterator().next().unregisterHandlerMethods(pluginId);
            unregisterRouterFunctions(pluginContext, hostContext);
            return;
        }
        Map<String, PmPluginRequestMappingHandlerMapping> mvcMappings = hostContext
            .getBeansOfType(PmPluginRequestMappingHandlerMapping.class);
        if (!mvcMappings.isEmpty()) {
            mvcMappings.values().iterator().next().unregisterController(pluginId);
        }
    }

    private static PmPluginAuthRegistry lookupAuthRegistry(ApplicationContext hostContext) {
        ObjectProvider<PmPluginAuthRegistry> provider = hostContext.getBeanProvider(PmPluginAuthRegistry.class);
        return provider.getIfAvailable();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerRouterFunctions(PmPluginAuthRegistry authRegistry, String pluginId,
            AnnotationConfigApplicationContext pluginContext, ApplicationContext hostContext) {
        Map<String, RouterFunction> routerFunctions = pluginContext.getBeansOfType(RouterFunction.class);
        if (routerFunctions.isEmpty()) {
            return;
        }
        PmPluginWebFluxRouterFunctionRegistry registry = hostContext
            .getBean(PmPluginWebFluxRouterFunctionRegistry.class);
        List<RouterFunction<ServerResponse>> functions = new ArrayList<>();
        for (RouterFunction routerFunction : routerFunctions.values()) {
            RouterFunction<ServerResponse> casted = (RouterFunction<ServerResponse>) routerFunction;
            if (routerFunction instanceof PmRouterFunctions.AnnotatedRouterFunction annotated) {
                functions.add(annotated.getDelegate());
            } else {
                functions.add(casted);
            }
        }
        registry.register(pluginId, functions);

        if (authRegistry != null) {
            Map<String, Set<String>> methodPatterns = new LinkedHashMap<>();
            for (RouterFunction routerFunction : routerFunctions.values()) {
                if (routerFunction instanceof PmRouterFunctions.AnnotatedRouterFunction annotated) {
                    for (AnonymousRouteDeclaration declaration : annotated.getDeclarations()) {
                        methodPatterns
                            .computeIfAbsent(declaration.httpMethod().toUpperCase(), key -> new LinkedHashSet<>())
                            .add(declaration.pathPattern());
                    }
                }
            }
            if (!methodPatterns.isEmpty()) {
                authRegistry.registerRoutes(pluginId, methodPatterns);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void unregisterRouterFunctions(AnnotationConfigApplicationContext pluginContext,
            ApplicationContext hostContext) {
        Map<String, PmPluginWebFluxRouterFunctionRegistry> registries = hostContext
            .getBeansOfType(PmPluginWebFluxRouterFunctionRegistry.class);
        if (registries.isEmpty()) {
            return;
        }
        Map<String, RouterFunction> current = pluginContext.getBeansOfType(RouterFunction.class);
        List<RouterFunction<ServerResponse>> functions = new ArrayList<>();
        for (RouterFunction routerFunction : current.values()) {
            RouterFunction<ServerResponse> casted = (RouterFunction<ServerResponse>) routerFunction;
            if (routerFunction instanceof PmRouterFunctions.AnnotatedRouterFunction annotated) {
                functions.add(annotated.getDelegate());
            } else {
                functions.add(casted);
            }
        }
        registries.values().iterator().next().unregister(pluginContext.getId(), functions);
    }

    private void registerMvcPluginRoutes(PmPluginAuthRegistry authRegistry, String pluginId,
            PmPluginRequestMappingHandlerMapping handlerMapping) {
        if (authRegistry == null) {
            return;
        }
        List<org.springframework.web.servlet.mvc.method.RequestMappingInfo> mappings = handlerMapping
            .getPluginMappingInfo()
            .get(pluginId);
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        Map<String, Set<String>> methodPatterns = new LinkedHashMap<>();
        for (org.springframework.web.servlet.mvc.method.RequestMappingInfo mapping : mappings) {
            collectServletMapping(methodPatterns, mapping);
        }
        if (!methodPatterns.isEmpty()) {
            authRegistry.registerRoutes(pluginId, methodPatterns);
        }
    }

    private void collectServletMapping(Map<String, Set<String>> methodPatterns,
            org.springframework.web.servlet.mvc.method.RequestMappingInfo mapping) {
        Set<String> patterns = mapping.getPatternValues();
        if (patterns == null || patterns.isEmpty()) {
            return;
        }
        collectMethods(methodPatterns, patterns, mapping.getMethodsCondition().getMethods());
    }

    private void registerWebFluxPluginRoutes(PmPluginAuthRegistry authRegistry, String pluginId,
            PmPluginWebFluxRequestMappingHandlerMapping handlerMapping) {
        if (authRegistry == null) {
            return;
        }
        List<org.springframework.web.reactive.result.method.RequestMappingInfo> mappings = handlerMapping
            .getPluginRequestMappingInfo()
            .get(pluginId);
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        Map<String, Set<String>> methodPatterns = new LinkedHashMap<>();
        for (org.springframework.web.reactive.result.method.RequestMappingInfo mapping : mappings) {
            Set<String> patterns = mapping.getPatternsCondition()
                .getPatterns()
                .stream()
                .map(pattern -> pattern.getPatternString())
                .collect(Collectors.toSet());
            collectMethods(methodPatterns, patterns, mapping.getMethodsCondition().getMethods());
        }
        if (!methodPatterns.isEmpty()) {
            authRegistry.registerRoutes(pluginId, methodPatterns);
        }
    }

    private void collectMethods(Map<String, Set<String>> methodPatterns, Set<String> patterns,
            Set<RequestMethod> methods) {
        if (methods.isEmpty()) {
            for (String method : ALL_METHODS) {
                methodPatterns.computeIfAbsent(method, key -> new LinkedHashSet<>()).addAll(patterns);
            }
        } else {
            for (RequestMethod method : methods) {
                methodPatterns.computeIfAbsent(method.name(), key -> new LinkedHashSet<>()).addAll(patterns);
            }
        }
    }
}
