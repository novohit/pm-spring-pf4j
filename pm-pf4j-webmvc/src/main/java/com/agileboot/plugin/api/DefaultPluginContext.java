package com.agileboot.plugin.api;

import com.agileboot.plugin.config.TenantPluginConfig;
import com.agileboot.plugin.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPluginContext implements PluginContext {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginContext.class);

    private final String pluginId;
    private final ApplicationContext pluginApplicationContext;
    private final TenantPluginConfig.PluginInstanceConfig pluginConfig;
    private final Map<Object, List<RequestMappingInfo>> registeredMappings = new ConcurrentHashMap<>();

    public DefaultPluginContext(String pluginId, ApplicationContext pluginApplicationContext,
                                TenantPluginConfig.PluginInstanceConfig pluginConfig) {
        this.pluginId = pluginId;
        this.pluginApplicationContext = pluginApplicationContext;
        this.pluginConfig = pluginConfig != null ? pluginConfig : new TenantPluginConfig.PluginInstanceConfig();
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return pluginApplicationContext;
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        try {
            // Plugin AC first (parent lookup is automatic via Spring)
            return pluginApplicationContext.getBean(serviceClass);
        } catch (Exception e) {
            log.warn("[{}] 无法获取服务: {} - {}", pluginId, serviceClass.getSimpleName(), e.getMessage());
            return null;
        }
    }

    @Override
    public TenantPluginConfig.PluginInstanceConfig getPluginConfig() {
        return pluginConfig;
    }

    @Override
    public void publishEvent(Object event) {
        EventBus eventBus = pluginApplicationContext.getBean(EventBus.class);
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    @Override
    public <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener) {
        EventBus eventBus = pluginApplicationContext.getBean(EventBus.class);
        eventBus.subscribe(eventType, listener);
    }

    @Override
    public <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener, String tenantId) {
        EventBus eventBus = pluginApplicationContext.getBean(EventBus.class);
        eventBus.subscribe(eventType, listener, tenantId);
    }

    @Override
    public <T> void unsubscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener) {
        EventBus eventBus = pluginApplicationContext.getBean(EventBus.class);
        eventBus.unsubscribe(eventType, listener);
    }

    /**
     * Auto-register all @RestController/@Controller beans from plugin AC into host MVC.
     * Called by the framework after plugin AC is refreshed.
     */
    public void autoRegisterControllers() {
        Set<Object> controllers = new HashSet<>();
        Map<String, Object> restControllers = pluginApplicationContext.getBeansWithAnnotation(RestController.class);
        Map<String, Object> controllers_ = pluginApplicationContext.getBeansWithAnnotation(Controller.class);
        controllers.addAll(restControllers.values());
        controllers.addAll(controllers_.values());

        for (Object controller : controllers) {
            registerController(controller);
        }

        if (!controllers.isEmpty()) {
            log.info("[{}] 自动注册{}个Controller", pluginId, controllers.size());
            // 注册 OpenAPI 分组，使 Swagger 文档包含插件接口
            ApplicationContext hostAC = pluginApplicationContext.getParent();
            if (hostAC != null) {
                com.agileboot.plugin.openapi.PluginOpenApiConfig.registerPluginOpenApi(hostAC, pluginId, controllers);
            }
        }
    }

    @Override
    public void registerController(Object controller) {
        try {
            RequestMappingHandlerMapping handlerMapping = getRequestMappingHandlerMapping();
            if (handlerMapping == null) {
                log.warn("[{}] 无法获取RequestMappingHandlerMapping", pluginId);
                return;
            }

            Class<?> controllerClass = controller.getClass();
            if (!controllerClass.isAnnotationPresent(RestController.class) &&
                    !controllerClass.isAnnotationPresent(Controller.class)) {
                log.warn("[{}] Controller没有@RestController或@Controller注解", pluginId);
                return;
            }

            RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
            String[] basePath = classMapping != null ? classMapping.value() : new String[]{""};

            List<RequestMappingInfo> mappings = new ArrayList<>();

            for (Method method : controllerClass.getDeclaredMethods()) {
                String[] paths = null;
                org.springframework.web.bind.annotation.RequestMethod[] methods = null;

                GetMapping getMapping = method.getAnnotation(GetMapping.class);
                PostMapping postMapping = method.getAnnotation(PostMapping.class);
                PutMapping putMapping = method.getAnnotation(PutMapping.class);
                DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);

                if (getMapping != null) {
                    paths = getMapping.value();
                    methods = new org.springframework.web.bind.annotation.RequestMethod[]{
                            org.springframework.web.bind.annotation.RequestMethod.GET
                    };
                } else if (postMapping != null) {
                    paths = postMapping.value();
                    methods = new org.springframework.web.bind.annotation.RequestMethod[]{
                            org.springframework.web.bind.annotation.RequestMethod.POST
                    };
                } else if (putMapping != null) {
                    paths = putMapping.value();
                    methods = new org.springframework.web.bind.annotation.RequestMethod[]{
                            org.springframework.web.bind.annotation.RequestMethod.PUT
                    };
                } else if (deleteMapping != null) {
                    paths = deleteMapping.value();
                    methods = new org.springframework.web.bind.annotation.RequestMethod[]{
                            org.springframework.web.bind.annotation.RequestMethod.DELETE
                    };
                }

                if (paths != null) {
                    for (String path : paths) {
                        String fullPath = basePath[0] + path;
                        RequestMappingInfo mappingInfo = RequestMappingInfo
                                .paths(fullPath)
                                .methods(methods)
                                .build();

                        handlerMapping.registerMapping(mappingInfo, controller, method);
                        mappings.add(mappingInfo);

                        log.info("[{}] 注册API端点: {} -> {}.{}",
                                pluginId, fullPath, controllerClass.getSimpleName(), method.getName());
                    }
                }
            }

            registeredMappings.put(controller, mappings);
            log.info("[{}] Controller注册成功: {} ({}个端点)", pluginId, controllerClass.getSimpleName(), mappings.size());

        } catch (Exception e) {
            log.error("[{}] Controller注册失败: {}", pluginId, e.getMessage(), e);
        }
    }

    @Override
    public void unregisterController(Object controller) {
        try {
            RequestMappingHandlerMapping handlerMapping = getRequestMappingHandlerMapping();
            if (handlerMapping == null) {
                return;
            }

            List<RequestMappingInfo> mappings = registeredMappings.remove(controller);
            if (mappings != null) {
                for (RequestMappingInfo mapping : mappings) {
                    handlerMapping.unregisterMapping(mapping);
                }
                log.info("[{}] Controller注销成功: {} ({}个端点)",
                        pluginId, controller.getClass().getSimpleName(), mappings.size());
            }
        } catch (Exception e) {
            log.error("[{}] Controller注销失败: {}", pluginId, e.getMessage(), e);
        }
    }

    public void unregisterAllControllers() {
        for (Object controller : new ArrayList<>(registeredMappings.keySet())) {
            unregisterController(controller);
        }
        // 清理 OpenAPI 注册
        ApplicationContext hostAC = pluginApplicationContext.getParent();
        if (hostAC != null) {
            com.agileboot.plugin.openapi.PluginOpenApiConfig.unregisterPluginOpenApi(hostAC, pluginId);
        }
    }

    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        try {
            // Get from parent (host) context
            if (pluginApplicationContext.getParent() != null) {
                return pluginApplicationContext.getParent().getBean(RequestMappingHandlerMapping.class);
            }
            return pluginApplicationContext.getBean(RequestMappingHandlerMapping.class);
        } catch (Exception e) {
            log.warn("[{}] 无法获取RequestMappingHandlerMapping: {}", pluginId, e.getMessage());
            return null;
        }
    }

    /**
     * Close the plugin's ApplicationContext.
     */
    public void close() {
        try {
            if (pluginApplicationContext instanceof ConfigurableApplicationContext) {
                ((ConfigurableApplicationContext) pluginApplicationContext).close();
                log.info("[{}] 插件ApplicationContext已关闭", pluginId);
            }
        } catch (Exception e) {
            log.error("[{}] 关闭ApplicationContext失败: {}", pluginId, e.getMessage(), e);
        }
    }
}
