package com.pmplugin4j.webmvc;

import com.pmplugin4j.core.AllowAnonymous;
import com.pmplugin4j.core.AnonymousPathEntry;
import com.pmplugin4j.core.PluginAnonymousPathRegistrar;
import com.pmplugin4j.core.PluginAuthenticated;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Request mapping handler that owns dynamically registered MVC routes for plugins. */
public class PmPluginRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(PmPluginRequestMappingHandlerMapping.class);
    private static final List<String> ALL_METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH");

    private final MultiValueMap<String, RequestMappingInfo> pluginMappingInfo = new LinkedMultiValueMap<>();
    private final Map<String, Map<String, Set<String>>> pluginPaths = new LinkedHashMap<>();
    private final Map<String, Map<String, Set<String>>> pluginAuthenticatedPaths = new LinkedHashMap<>();
    private PluginAnonymousPathRegistrar anonymousPathRegistrar;

    public MultiValueMap<String, RequestMappingInfo> getPluginMappingInfo() {
        return pluginMappingInfo;
    }

    public void setAnonymousPathRegistry(PluginAnonymousPathRegistrar anonymousPathRegistrar) {
        this.anonymousPathRegistrar = anonymousPathRegistrar;
    }

    @Override
    public void detectHandlerMethods(Object controller) {
    }

    @Override
    protected void initHandlerMethods() {
    }

    public Set<Object> registerControllers(String pluginId, ApplicationContext context) {
        long startTime = System.currentTimeMillis();
        Set<Object> controllers = getControllerBeans(context);
        List<String> controllerNames = controllers.stream()
            .map(controller -> controller.getClass().getSimpleName())
            .collect(Collectors.toList());
        log.info("Found {} controllers in plugin {}: {}", controllers.size(), pluginId, controllerNames);
        int anonymousCount = 0;
        for (Object controller : controllers) {
            anonymousCount += registerController(pluginId, controller);
        }
        log.info("Successfully registered {} controllers for plugin: {} (took {} ms), including {} anonymous endpoints",
                controllers.size(), pluginId, System.currentTimeMillis() - startTime, anonymousCount);
        return controllers;
    }

    private int registerController(String pluginId, Object controller) {
        String controllerClassName = controller.getClass().getName();
        Class<?> handlerType = controller.getClass();
        Class<?> userType = ClassUtils.getUserClass(handlerType);
        MethodIntrospector.MetadataLookup<RequestMappingInfo> metadataLookup = method -> super.getMappingForMethod(
                method, handlerType);
        Map<Method, RequestMappingInfo> methods = MethodIntrospector.selectMethods(userType, metadataLookup);
        AllowAnonymous classAnonymous = userType.getAnnotation(AllowAnonymous.class);
        PluginAuthenticated classAuthenticated = userType.getAnnotation(PluginAuthenticated.class);
        int[] anonymousCount = {0};
        methods.forEach((method, mapping) -> {
            Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
            super.registerHandlerMethod(controller, invocableMethod, mapping);
            pluginMappingInfo.add(pluginId, mapping);
            collectPluginPaths(pluginId, mapping, pluginPaths);
            PluginAuthenticated methodAuthenticated = method.getAnnotation(PluginAuthenticated.class);
            if ((methodAuthenticated != null || classAuthenticated != null)
                    && method.getAnnotation(AllowAnonymous.class) == null && classAnonymous == null) {
                collectPluginPaths(pluginId, mapping, pluginAuthenticatedPaths);
            }
            if (anonymousPathRegistrar != null) {
                AllowAnonymous methodAnonymous = method.getAnnotation(AllowAnonymous.class);
                AllowAnonymous effectiveAnonymous = methodAnonymous != null ? methodAnonymous : classAnonymous;
                if (effectiveAnonymous != null) {
                    if (effectiveAnonymous.reason().isBlank()) {
                        log.warn(
                                "[Plugin: {}] @AllowAnonymous on {}.{}() has blank reason — registration as anonymous is blocked.",
                                pluginId, controllerClassName, method.getName());
                    } else {
                        anonymousCount[0] += registerAnonymousPaths(pluginId, controllerClassName, method.getName(),
                                mapping, effectiveAnonymous.reason());
                    }
                }
            }
        });
        return anonymousCount[0];
    }

    private int registerAnonymousPaths(String pluginId, String controllerClass, String methodName,
            RequestMappingInfo mapping, String reason) {
        Set<String> patterns = mapping.getPatternValues();
        if (patterns == null || patterns.isEmpty()) {
            return 0;
        }
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        int count = 0;
        for (String pattern : patterns) {
            if (methods.isEmpty()) {
                anonymousPathRegistrar.register(pluginId, new AnonymousPathEntry(pluginId, pattern, "*",
                        controllerClass, methodName, reason, LocalDateTime.now()));
                count++;
            } else {
                for (RequestMethod httpMethod : methods) {
                    anonymousPathRegistrar.register(pluginId, new AnonymousPathEntry(pluginId, pattern,
                            httpMethod.name(), controllerClass, methodName, reason, LocalDateTime.now()));
                    count++;
                }
            }
        }
        return count;
    }

    private Set<Object> getControllerBeans(ApplicationContext context) {
        Set<Object> beans = new LinkedHashSet<>();
        beans.addAll(context.getBeansWithAnnotation(Controller.class).values());
        beans.addAll(context.getBeansWithAnnotation(RestController.class).values());
        return beans;
    }

    public void unregisterController(String pluginId) {
        if (anonymousPathRegistrar != null) {
            anonymousPathRegistrar.unregister(pluginId);
        }
        pluginPaths.remove(pluginId);
        pluginAuthenticatedPaths.remove(pluginId);
        List<RequestMappingInfo> mappings = pluginMappingInfo.remove(pluginId);
        if (mappings == null) {
            return;
        }
        mappings.forEach(this::unregisterMapping);
        log.debug("Unregistered {} routes for plugin: {}", mappings.size(), pluginId);
    }

    public Map<String, Set<String>> getPluginPaths(String pluginId) {
        return pluginPaths.getOrDefault(pluginId, Map.of());
    }

    public Map<String, Set<String>> getPluginAuthenticatedPaths(String pluginId) {
        return pluginAuthenticatedPaths.getOrDefault(pluginId, Map.of());
    }

    private void collectPluginPaths(String pluginId, RequestMappingInfo mapping,
            Map<String, Map<String, Set<String>>> destination) {
        Set<String> patterns = mapping.getPatternValues();
        if (patterns == null || patterns.isEmpty()) {
            return;
        }
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        Map<String, Set<String>> pluginMap = destination.computeIfAbsent(pluginId, key -> new LinkedHashMap<>());
        if (methods.isEmpty()) {
            for (String method : ALL_METHODS) {
                pluginMap.computeIfAbsent(method, key -> new LinkedHashSet<>()).addAll(patterns);
            }
        } else {
            for (RequestMethod method : methods) {
                pluginMap.computeIfAbsent(method.name(), key -> new LinkedHashSet<>()).addAll(patterns);
            }
        }
    }
}
