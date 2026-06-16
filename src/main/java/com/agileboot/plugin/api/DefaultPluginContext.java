package com.agileboot.plugin.api;

import com.agileboot.plugin.config.TenantPluginConfig;
import com.agileboot.plugin.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认插件上下文实现
 */
public class DefaultPluginContext implements PluginContext {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginContext.class);

    private final String pluginId;
    private final ApplicationContext applicationContext;
    private final TenantPluginConfig.PluginInstanceConfig pluginConfig;

    // 跟踪已注册的Controller和映射
    private final Map<Object, List<RequestMappingInfo>> registeredMappings = new ConcurrentHashMap<>();

    public DefaultPluginContext(String pluginId, ApplicationContext applicationContext,
                                TenantPluginConfig.PluginInstanceConfig pluginConfig) {
        this.pluginId = pluginId;
        this.applicationContext = applicationContext;
        this.pluginConfig = pluginConfig != null ? pluginConfig : new TenantPluginConfig.PluginInstanceConfig();
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        try {
            return applicationContext.getBean(serviceClass);
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
        EventBus eventBus = applicationContext.getBean(EventBus.class);
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    @Override
    public <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener) {
        EventBus eventBus = applicationContext.getBean(EventBus.class);
        eventBus.subscribe(eventType, listener);
    }

    @Override
    public <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener, String tenantId) {
        EventBus eventBus = applicationContext.getBean(EventBus.class);
        eventBus.subscribe(eventType, listener, tenantId);
    }

    @Override
    public <T> void unsubscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener) {
        EventBus eventBus = applicationContext.getBean(EventBus.class);
        eventBus.unsubscribe(eventType, listener);
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
            if (!controllerClass.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class) &&
                    !controllerClass.isAnnotationPresent(org.springframework.stereotype.Controller.class)) {
                log.warn("[{}] Controller没有@RestController或@Controller注解", pluginId);
                return;
            }

            // 获取类级别路径前缀
            org.springframework.web.bind.annotation.RequestMapping classMapping =
                    controllerClass.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
            String[] basePath = classMapping != null ? classMapping.value() : new String[]{""};

            List<RequestMappingInfo> mappings = new ArrayList<>();

            // 遍历所有方法，逐个注册
            for (Method method : controllerClass.getDeclaredMethods()) {
                String[] paths = null;
                org.springframework.web.bind.annotation.RequestMethod[] methods = null;

                org.springframework.web.bind.annotation.GetMapping getMapping =
                        method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
                org.springframework.web.bind.annotation.PostMapping postMapping =
                        method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
                org.springframework.web.bind.annotation.PutMapping putMapping =
                        method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
                org.springframework.web.bind.annotation.DeleteMapping deleteMapping =
                        method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class);

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

            // 记录映射，用于后续卸载
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

    /**
     * 注销所有Controller
     */
    public void unregisterAllControllers() {
        for (Object controller : new ArrayList<>(registeredMappings.keySet())) {
            unregisterController(controller);
        }
    }

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        try {
            return applicationContext.getBean(RequestMappingHandlerMapping.class);
        } catch (Exception e) {
            log.warn("[{}] 无法获取RequestMappingHandlerMapping: {}", pluginId, e.getMessage());
            return null;
        }
    }
}
