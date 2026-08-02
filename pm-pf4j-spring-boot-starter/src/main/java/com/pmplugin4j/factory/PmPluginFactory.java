package com.pmplugin4j.factory;

import com.pmplugin4j.api.PmPlugin;
import com.pmplugin4j.api.PmSpringPlugin;
import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import com.pmplugin4j.mybatis.PluginMybatisRegistrar;
import com.pmplugin4j.webmvc.PluginOpenApiRegistrar;
import com.pmplugin4j.webmvc.PluginWebMvcRegistrar;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import org.pf4j.Plugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationContext;

/** Creates the business plugin and wraps it with the Spring-aware PF4J lifecycle. */
public final class PmPluginFactory implements PluginFactory {

    private final ApplicationContext hostApplicationContext;
    private final PluginProperties pluginProperties;
    private final List<PluginResourceRegistrar> programmaticRegistrars;

    public PmPluginFactory(ApplicationContext hostApplicationContext, PluginProperties pluginProperties,
            List<PluginResourceRegistrar> programmaticRegistrars) {
        this.hostApplicationContext = hostApplicationContext;
        this.pluginProperties = pluginProperties;
        this.programmaticRegistrars = List.copyOf(programmaticRegistrars);
    }

    @Override
    public Plugin create(PluginWrapper wrapper) {
        try {
            Class<?> pluginClass = wrapper.getPluginClassLoader().loadClass(wrapper.getDescriptor().getPluginClass());
            int modifiers = pluginClass.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)
                    || !PmPlugin.class.isAssignableFrom(pluginClass)) {
                throw new IllegalArgumentException(
                        "Plugin class must be a concrete PmPlugin: " + pluginClass.getName());
            }
            PmPlugin businessPlugin = instantiate(pluginClass, wrapper);
            return new PmSpringPlugin(wrapper, businessPlugin, hostApplicationContext, pluginProperties,
                    List.of(new PluginMybatisRegistrar(), new PluginWebMvcRegistrar(), new PluginOpenApiRegistrar()),
                    programmaticRegistrars);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to instantiate plugin " + wrapper.getPluginId(), exception);
        }
    }

    private static PmPlugin instantiate(Class<?> pluginClass, PluginWrapper wrapper)
            throws ReflectiveOperationException {
        try {
            Constructor<?> constructor = pluginClass.getConstructor(PluginWrapper.class);
            return (PmPlugin) constructor.newInstance(wrapper);
        } catch (NoSuchMethodException ignored) {
            Constructor<?> constructor = pluginClass.getConstructor();
            return (PmPlugin) constructor.newInstance();
        }
    }
}
