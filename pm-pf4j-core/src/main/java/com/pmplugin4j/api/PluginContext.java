package com.pmplugin4j.api;

import com.pmplugin4j.config.TenantPluginConfig;
import com.pmplugin4j.event.EventBus;
import org.springframework.context.ApplicationContext;

/**
 * 插件上下文接口
 * <p>
 * 提供插件访问宿主应用的能力 通过PmPluginFactory注入到每个插件实例
 */
public interface PluginContext {

    /**
     * 获取插件ID
     */
    String getPluginId();

    /**
     * 获取Spring ApplicationContext
     */
    ApplicationContext getApplicationContext();

    /**
     * 获取Spring Bean
     */
    <T> T getService(Class<T> serviceClass);

    /**
     * 获取插件配置
     */
    TenantPluginConfig.PluginInstanceConfig getPluginConfig();

    /**
     * 发布事件
     */
    void publishEvent(Object event);

    /**
     * 订阅事件
     */
    <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener);

    /**
     * 订阅事件（带租户过滤）
     */
    <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener, String tenantId);

    /**
     * 取消订阅事件
     */
    <T> void unsubscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener);

    /**
     * 注册Controller到Spring MVC
     */
    void registerController(Object controller);

    /**
     * 注销Controller
     */
    void unregisterController(Object controller);

}
