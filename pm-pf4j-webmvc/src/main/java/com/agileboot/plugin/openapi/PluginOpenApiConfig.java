package com.agileboot.plugin.openapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.api.AbstractOpenApiResource;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Field;
import java.util.*;

public class PluginOpenApiConfig {

    private static final Logger log = LoggerFactory.getLogger(PluginOpenApiConfig.class);
    private static final String BEAN_PREFIX = "pluginOpenApi-";
    private static final Map<String, List<Class<?>>> PLUGIN_CONTROLLER_CLASSES = new HashMap<>();

    /**
     * 为插件注册 OpenAPI 分组，使 springdoc 能扫描到插件的 Controller。
     */
    public static void registerPluginOpenApi(ApplicationContext hostCtx, String pluginId,
                                              Set<Object> controllers) {
        if (controllers.isEmpty()) return;

        try {
            String groupName = pluginId.trim().toLowerCase();
            String beanName = BEAN_PREFIX + groupName;

            // 收集 controller 包路径和类
            Set<String> packages = new HashSet<>();
            List<Class<?>> controllerClasses = new ArrayList<>();
            for (Object ctrl : controllers) {
                Class<?> clazz = ctrl.getClass();
                packages.add(clazz.getPackage().getName());
                controllerClasses.add(clazz);
            }

            // 1. 创建 GroupedOpenApi bean
            GroupedOpenApi groupedOpenApi = GroupedOpenApi.builder()
                    .group(groupName)
                    .displayName(pluginId)
                    .packagesToScan(packages.toArray(new String[0]))
                    .build();

            GenericApplicationContext ctx = (GenericApplicationContext) hostCtx;
            if (!ctx.containsBeanDefinition(beanName)) {
                ctx.getBeanFactory().registerSingleton(beanName, groupedOpenApi);
            }

            // 2. 告知 springdoc 这些 Controller 类
            AbstractOpenApiResource.addRestControllers(controllerClasses.toArray(new Class<?>[0]));
            PLUGIN_CONTROLLER_CLASSES.put(pluginId, controllerClasses);

            // 3. 注入到 MultipleOpenApiResource
            Object resource = findMultipleOpenApiResource(hostCtx);
            if (resource != null) {
                Field field = getGroupedOpenApisField(resource);
                @SuppressWarnings("unchecked")
                List<GroupedOpenApi> groupedOpenApis = (List<GroupedOpenApi>) field.get(resource);
                groupedOpenApis.add(groupedOpenApi);
                resource.getClass().getMethod("afterPropertiesSet").invoke(resource);
            }

            log.info("[{}] OpenAPI分组注册成功: {} ({}个Controller)", pluginId, groupName, controllerClasses.size());
        } catch (Exception e) {
            log.warn("[{}] OpenAPI注册失败 (Swagger文档可能不完整): {}", pluginId, e.getMessage());
        }
    }

    /**
     * 插件卸载时清理 OpenAPI 注册。
     */
    public static void unregisterPluginOpenApi(ApplicationContext hostCtx, String pluginId) {
        try {
            List<Class<?>> controllerClasses = PLUGIN_CONTROLLER_CLASSES.remove(pluginId);
            if (controllerClasses == null) return;

            // 从 ADDITIONAL_REST_CONTROLLERS 移除
            Field field = AbstractOpenApiResource.class.getDeclaredField("ADDITIONAL_REST_CONTROLLERS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Class<?>> additional = (List<Class<?>>) field.get(null);
            additional.removeAll(controllerClasses);

            // 从 MultipleOpenApiResource 移除分组
            Object resource = findMultipleOpenApiResource(hostCtx);
            if (resource != null) {
                Field groupedField = getGroupedOpenApisField(resource);
                @SuppressWarnings("unchecked")
                List<GroupedOpenApi> groupedOpenApis = (List<GroupedOpenApi>) groupedField.get(resource);
                groupedOpenApis.removeIf(g -> g.getGroup().equals(pluginId.trim().toLowerCase()));
            }

            // 销毁 bean
            String beanName = BEAN_PREFIX + pluginId.trim().toLowerCase();
            GenericApplicationContext ctx = (GenericApplicationContext) hostCtx;
            if (ctx.containsBeanDefinition(beanName)) {
                ctx.removeBeanDefinition(beanName);
            }

            log.info("[{}] OpenAPI分组注销成功", pluginId);
        } catch (Exception e) {
            log.warn("[{}] OpenAPI注销失败: {}", pluginId, e.getMessage());
        }
    }

    private static Object findMultipleOpenApiResource(ApplicationContext ctx) {
        try {
            Class<?> mvcClass = Class.forName("org.springdoc.webmvc.api.MultipleOpenApiResource");
            Map<String, ?> beans = ctx.getBeansOfType(mvcClass);
            if (!beans.isEmpty()) return beans.values().iterator().next();
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class<?> webfluxClass = Class.forName("org.springdoc.webflux.api.MultipleOpenApiResource");
            Map<String, ?> beans = ctx.getBeansOfType(webfluxClass);
            if (!beans.isEmpty()) return beans.values().iterator().next();
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private static Field getGroupedOpenApisField(Object resource) {
        Class<?> clazz = resource.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField("groupedOpenApis");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new IllegalArgumentException("groupedOpenApis field not found on " + resource.getClass().getName());
    }
}
