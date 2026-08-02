package com.pmplugin4j.api;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.config.TenantPluginConfig;
import com.pmplugin4j.factory.PluginBeanNameGenerator;
import com.pmplugin4j.lifecycle.PluginLifecycleEngine;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import java.util.List;
import java.util.Map;
import org.pf4j.Plugin;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Owns one plugin's Spring context and binds it to PF4J start/stop semantics. */
public final class PmSpringPlugin extends Plugin {

    private final PmPlugin businessPlugin;
    private final ApplicationContext hostContext;
    private final PluginProperties pluginProperties;
    private final PluginLifecycleEngine lifecycleEngine;
    private AnnotationConfigApplicationContext applicationContext;
    private boolean everStarted;

    public PmSpringPlugin(
            PluginWrapper wrapper,
            PmPlugin businessPlugin,
            ApplicationContext hostContext,
            PluginProperties pluginProperties,
            List<PluginResourceRegistrar> builtInRegistrars,
            List<PluginResourceRegistrar> programmaticRegistrars) {
        super(wrapper);
        this.businessPlugin = businessPlugin;
        this.hostContext = hostContext;
        this.pluginProperties = pluginProperties;
        this.lifecycleEngine = PluginLifecycleEngine.create(
                hostContext, builtInRegistrars, programmaticRegistrars);
    }

    @Override
    public void start() {
        AnnotationConfigApplicationContext baseContext = preCreateApplicationContext();
        AnnotationConfigApplicationContext customized =
                businessPlugin.beforeApplicationContextRefresh(baseContext);
        applicationContext = customized != null ? customized : baseContext;
        try {
            preparePluginContext(applicationContext);
            lifecycleEngine.executePhase(
                    PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, applicationContext);
            applicationContext.refresh();
            businessPlugin.afterApplicationContextReady(applicationContext);
            lifecycleEngine.executePhase(
                    PluginLifecyclePhase.AFTER_CONTEXT_REFRESH, applicationContext);
            everStarted = true;
        } catch (Exception exception) {
            closeFailedContext();
            throw new PluginRuntimeException(
                    "Plugin " + getWrapper().getPluginId() + " failed to start", exception);
        }
    }

    @Override
    public void stop() {
        if (applicationContext == null) {
            return;
        }
        try {
            lifecycleEngine.executePhase(
                    PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE, applicationContext);
        } finally {
            applicationContext.close();
            applicationContext = null;
            businessPlugin.attachPluginContext(null);
        }
    }

    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("Plugin application context is not available");
        }
        return applicationContext;
    }

    public boolean isEverStarted() {
        return everStarted;
    }

    private AnnotationConfigApplicationContext preCreateApplicationContext() {
        String pluginId = getWrapper().getPluginId();
        String pluginClassName = getWrapper().getDescriptor().getPluginClass();
        String basePackage = pluginClassName.substring(0, pluginClassName.lastIndexOf('.'));
        if (!basePackage.equals(pluginId)) {
            throw new PluginRuntimeException(
                    "Plugin ID must match the business plugin package: id="
                            + pluginId + ", package=" + basePackage);
        }
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId(pluginId);
        context.setParent(hostContext);
        context.setClassLoader(getWrapper().getPluginClassLoader());
        context.setBeanNameGenerator(new PluginBeanNameGenerator(pluginId));
        ConfigurationPropertiesBindingPostProcessor.register(context);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "pmPlugin",
                Map.of("pm.plugin.id", pluginId, "pm.plugin.base-package", basePackage)));
        context.scan(basePackage);
        return context;
    }

    private void preparePluginContext(AnnotationConfigApplicationContext context) {
        String pluginId = getWrapper().getPluginId();
        TenantPluginConfig.PluginInstanceConfig config = null;
        if (pluginProperties.getCurrentTenant() != null) {
            config = pluginProperties.getPluginConfig(
                    pluginId, pluginProperties.getCurrentTenant());
        }
        DefaultPluginContext pluginContext = new DefaultPluginContext(pluginId, context, config);
        businessPlugin.attachPluginContext(pluginContext);
        context.getBeanFactory().registerSingleton("pluginContext", pluginContext);
        context.getBeanFactory().registerSingleton("pmPluginId", pluginId);
    }

    private void closeFailedContext() {
        if (applicationContext != null) {
            if (applicationContext.isActive()) {
                lifecycleEngine.executePhase(
                        PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE, applicationContext);
            }
            applicationContext.close();
            applicationContext = null;
        }
        businessPlugin.attachPluginContext(null);
    }
}
