package com.pmplugin4j.api;

import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Business plugin customized and hosted by the framework's Spring-aware PF4J wrapper. */
public abstract class PmPlugin {

    private final PluginWrapper wrapper;
    private PluginContext pluginContext;

    protected PmPlugin() {
        this.wrapper = null;
    }

    protected PmPlugin(PluginWrapper wrapper) {
        this.wrapper = wrapper;
    }

    protected abstract AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context);

    protected abstract void afterApplicationContextReady(AnnotationConfigApplicationContext context);

    public final void attachPluginContext(PluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    protected final PluginContext getContext() {
        if (pluginContext == null) {
            throw new IllegalStateException("Plugin context is not available before plugin start");
        }
        return pluginContext;
    }

    protected final <T> T getService(Class<T> serviceClass) {
        return getContext().getService(serviceClass);
    }

    protected final PluginWrapper getWrapper() {
        return wrapper;
    }
}
