package com.pmplugin4j.api;

import com.pmplugin4j.config.TenantPluginConfig;
import com.pmplugin4j.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Default plugin-facing context backed by the plugin child application context. */
public class DefaultPluginContext implements PluginContext {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginContext.class);

    private final String pluginId;
    private final ApplicationContext pluginApplicationContext;
    private final TenantPluginConfig.PluginInstanceConfig pluginConfig;

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
            return pluginApplicationContext.getBean(serviceClass);
        } catch (Exception exception) {
            log.warn("[{}] Unable to get service {}: {}", pluginId, serviceClass.getSimpleName(),
                    exception.getMessage());
            return null;
        }
    }

    @Override
    public TenantPluginConfig.PluginInstanceConfig getPluginConfig() {
        return pluginConfig;
    }

    @Override
    public void publishEvent(Object event) {
        pluginApplicationContext.getBean(EventBus.class).publish(event);
    }

    @Override
    public <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener) {
        pluginApplicationContext.getBean(EventBus.class).subscribe(eventType, listener);
    }

    @Override
    public <T> void subscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener, String tenantId) {
        pluginApplicationContext.getBean(EventBus.class).subscribe(eventType, listener, tenantId);
    }

    @Override
    public <T> void unsubscribeEvent(Class<T> eventType, EventBus.EventListener<T> listener) {
        pluginApplicationContext.getBean(EventBus.class).unsubscribe(eventType, listener);
    }

    public void close() {
        if (pluginApplicationContext instanceof ConfigurableApplicationContext configurableContext) {
            configurableContext.close();
        }
    }
}
