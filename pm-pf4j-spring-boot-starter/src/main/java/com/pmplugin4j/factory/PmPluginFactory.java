package com.pmplugin4j.factory;

import com.pmplugin4j.api.PmPlugin;
import com.pmplugin4j.api.PmSpringPlugin;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import com.pmplugin4j.manager.PmPluginManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import org.pf4j.Plugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/** Creates the business plugin and wraps it with the Spring-aware PF4J lifecycle. */
public final class PmPluginFactory implements PluginFactory {

    private static final Logger log = LoggerFactory.getLogger(PmPluginFactory.class);

    @Override
    public Plugin create(PluginWrapper wrapper) {
        try {
            String pluginClassName = wrapper.getDescriptor().getPluginClass();
            log.debug("Create instance for plugin '{}'", pluginClassName);
            Class<?> pluginClass;
            try {
                pluginClass = wrapper.getPluginClassLoader().loadClass(pluginClassName);
            } catch (ClassNotFoundException exception) {
                throw new IllegalArgumentException(
                        "Class " + pluginClassName + " not found, plugin or additional paths");
            }
            int modifiers = pluginClass.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)
                    || !PmPlugin.class.isAssignableFrom(pluginClass)) {
                throw new IllegalArgumentException("The plugin class " + pluginClassName + " is not valid");
            }
            PmPlugin businessPlugin = instantiate(pluginClass, wrapper);
            PmPluginManager pluginManager = (PmPluginManager) wrapper.getPluginManager();
            List<PluginResourceRegistrar> programmaticRegistrars = pluginManager.getExternalRegistrars();
            return new PmSpringPlugin(wrapper, businessPlugin, pluginManager.getMainApplicationContext(),
                    pluginManager.getPluginProperties(), List.of(), programmaticRegistrars);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to instantiate plugin：" + exception.getMessage(), exception);
        }
    }

    private static PmPlugin instantiate(Class<?> pluginClass, PluginWrapper wrapper) {
        try {
            Constructor<?> constructor = pluginClass.getConstructor(PluginWrapper.class);
            return (PmPlugin) constructor.newInstance(wrapper);
        } catch (NoSuchMethodException exception) {
            return createUsingNoParametersConstructor(pluginClass);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new IllegalArgumentException("Failed to instantiate plugin class [" + pluginClass.getName()
                    + "] with PluginWrapper parameter constructor", exception);
        }
    }

    private static PmPlugin createUsingNoParametersConstructor(Class<?> pluginClass) {
        try {
            Constructor<?> constructor = pluginClass.getConstructor();
            return (PmPlugin) constructor.newInstance();
        } catch (NoSuchMethodException exception) {
            log.error(exception.getMessage(), exception);
            throw new IllegalArgumentException("Plugin class [" + pluginClass.getName()
                    + "] has no valid constructor. Require a constructor with PluginWrapper parameter or a no-arg constructor",
                    exception);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new IllegalArgumentException(
                    "Failed to instantiate plugin class [" + pluginClass.getName() + "] with no-arg constructor",
                    exception);
        }
    }
}
