package com.pmplugin4j.api;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.config.TenantPluginConfig;
import com.pmplugin4j.descriptor.PmPluginDescriptor;
import com.pmplugin4j.event.PmPluginRestartedEvent;
import com.pmplugin4j.event.PmPluginStartingEvent;
import com.pmplugin4j.event.PmPluginStoppedEvent;
import com.pmplugin4j.factory.PluginBeanNameGenerator;
import com.pmplugin4j.lifecycle.PluginLifecycleEngine;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import java.util.List;
import java.util.Map;
import org.pf4j.Plugin;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Owns one plugin's Spring context and binds it to PF4J start/stop semantics. */
public final class PmSpringPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(PmSpringPlugin.class);

    private final PmPlugin businessPlugin;
    private final ApplicationContext hostContext;
    private final PluginProperties pluginProperties;
    private final PluginLifecycleEngine lifecycleEngine;
    private AnnotationConfigApplicationContext applicationContext;
    private boolean everStarted;

    public PmSpringPlugin(PluginWrapper wrapper, PmPlugin businessPlugin, ApplicationContext hostContext,
            PluginProperties pluginProperties, List<PluginResourceRegistrar> builtInRegistrars,
            List<PluginResourceRegistrar> programmaticRegistrars) {
        super(wrapper);
        this.businessPlugin = businessPlugin;
        this.hostContext = hostContext;
        this.pluginProperties = pluginProperties;
        this.lifecycleEngine = PluginLifecycleEngine.create(hostContext, builtInRegistrars, programmaticRegistrars);
    }

    @Override
    public void start() {
        final long startTimestamp = System.currentTimeMillis();
        log.info("Starting plugin '{}' ......", getWrapper().getPluginId());
        try {
            applicationContext = createApplicationContext();
            PmPluginDescriptor descriptor = (PmPluginDescriptor) getWrapper().getDescriptor();
            applicationContext.publishEvent(new PmPluginStartingEvent(applicationContext, descriptor));
            if (everStarted) {
                applicationContext.publishEvent(new PmPluginRestartedEvent(descriptor));
            }
            everStarted = true;
            log.info("Plugin '{}' is started in {}ms", getWrapper().getPluginId(),
                    System.currentTimeMillis() - startTimestamp);
        } catch (Exception exception) {
            closeFailedContext();
            throw new PluginRuntimeException("Plugin " + getWrapper().getPluginId() + " failed to start", exception);
        }
    }

    @Override
    public void stop() {
        if (applicationContext == null) {
            log.warn(
                    "Plugin '{}' stop() called but ApplicationContext is null (start may have failed), nothing to stop.",
                    getWrapper().getPluginId());
            return;
        }
        log.info("Stopping plugin '{}' ......", getWrapper().getPluginId());
        try {
            lifecycleEngine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE, applicationContext);
            applicationContext.publishEvent(
                    new PmPluginStoppedEvent(applicationContext, (PmPluginDescriptor) getWrapper().getDescriptor()));
        } finally {
            applicationContext.close();
            applicationContext = null;
            businessPlugin.attachPluginContext(null);
        }
        log.info("Plugin '{}' is stopped", getWrapper().getPluginId());
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
            throw new PluginRuntimeException("Plugin package mismatch: plugin.id from plugin.properties is '" + pluginId
                    + "', but PmPlugin subclass '" + pluginClassName + "' is in package '" + basePackage
                    + "'. The plugin.id must exactly match the package name of the PmPlugin subclass.");
        }
        int lastDotIndex = pluginId.lastIndexOf('.');
        if (lastDotIndex < 0) {
            throw new PluginRuntimeException("plugin.id '" + pluginId
                    + "' is not a fully qualified name. plugin.id must contain at least one dot "
                    + "(e.g. 'com.example.myplugin'), because its last segment is used as the plugin's bean name prefix.");
        }
        String home = pluginId.substring(lastDotIndex + 1);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId(pluginId);
        context.setParent(hostContext);
        context.setClassLoader(getWrapper().getPluginClassLoader());
        context.setBeanNameGenerator(new PluginBeanNameGenerator(home));
        ConfigurationPropertiesBindingPostProcessor.register(context);
        context.getEnvironment()
            .getPropertySources()
            .addFirst(new MapPropertySource("pmPlugin",
                    Map.of("pm.plugin.id", pluginId, "pm.plugin.base-package", basePackage)));
        context.scan(basePackage);
        return context;
    }

    private AnnotationConfigApplicationContext createApplicationContext() {
        final long startTimestamp = System.currentTimeMillis();
        String pluginId = getWrapper().getPluginId();
        // Step 1: Pre-create application context
        log.info("Initializing base context for plugin '{}'", pluginId);
        long preCreateStart = System.currentTimeMillis();
        AnnotationConfigApplicationContext annotationContext = preCreateApplicationContext();
        applicationContext = annotationContext;
        log.info("Initialized base context for plugin '{}' in {} ms", pluginId,
                System.currentTimeMillis() - preCreateStart);
        // Step 2: Customize context before refresh
        log.info("Customizing context configuration for plugin '{}'", pluginId);
        long handleStart = System.currentTimeMillis();
        AnnotationConfigApplicationContext context = businessPlugin.beforeApplicationContextRefresh(annotationContext);
        log.info("Customized context configuration for plugin '{}' in {} ms", pluginId,
                System.currentTimeMillis() - handleStart);
        if (context == null) {
            context = annotationContext;
        }
        applicationContext = context;
        preparePluginContext(context);
        // Step 3: registerPluginResources
        lifecycleEngine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, context);
        // Step 4: Refresh the context (load beans, etc.)
        log.info("Refreshing Spring context for plugin '{}'", pluginId);
        long refreshStart = System.currentTimeMillis();
        postCreateApplicationContext(context);
        log.info("Refreshed Spring context for plugin '{}' in {} ms", pluginId,
                System.currentTimeMillis() - refreshStart);
        // Step 5: Post-refresh custom logic
        log.info("Executing post-refresh logic for plugin '{}'", pluginId);
        long customStart = System.currentTimeMillis();
        businessPlugin.afterApplicationContextReady(context);
        lifecycleEngine.executePhase(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH, context);
        log.info("Completed post-refresh logic for plugin '{}' in {} ms", pluginId,
                System.currentTimeMillis() - customStart);
        log.info("Plugin '{}' context fully initialized in {} ms", pluginId,
                System.currentTimeMillis() - startTimestamp);
        return context;
    }

    private void postCreateApplicationContext(AnnotationConfigApplicationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("AnnotationConfigApplicationContext cannot be null");
        }
        if (context.isActive()) {
            return;
        }
        try {
            context.refresh();
        } catch (Exception exception) {
            throw new PluginRuntimeException(
                    "[Plugin: " + getWrapper().getPluginId() + "] Startup failed: " + exception.getMessage(),
                    exception);
        }
    }

    private void preparePluginContext(AnnotationConfigApplicationContext context) {
        String pluginId = getWrapper().getPluginId();
        TenantPluginConfig.PluginInstanceConfig config = null;
        if (pluginProperties.getCurrentTenant() != null) {
            config = pluginProperties.getPluginConfig(pluginId, pluginProperties.getCurrentTenant());
        }
        DefaultPluginContext pluginContext = new DefaultPluginContext(pluginId, context, config);
        businessPlugin.attachPluginContext(pluginContext);
        context.getBeanFactory().registerSingleton("pluginContext", pluginContext);
        context.getBeanFactory().registerSingleton("pmPluginId", pluginId);
    }

    private void closeFailedContext() {
        if (applicationContext != null) {
            if (applicationContext.isActive()) {
                lifecycleEngine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE, applicationContext);
            }
            applicationContext.close();
            applicationContext = null;
        }
        businessPlugin.attachPluginContext(null);
    }
}
