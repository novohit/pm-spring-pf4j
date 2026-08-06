package com.pmplugin4j.manager;

import com.pmplugin4j.api.PmSpringPlugin;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.support.GenericApplicationContext;

public class PmPluginExtensionFactory implements ExtensionFactory {
    private final PmPluginManager pluginManager;

    public PmPluginExtensionFactory(PmPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public <T> T create(Class<T> extensionClass) {
        GenericApplicationContext context = getApplicationContext(extensionClass);
        try {
            return context.getBean(extensionClass);
        } catch (NoSuchBeanDefinitionException ignored) {
            T extension = createWithoutSpring(extensionClass);
            context.getBeanFactory().registerSingleton(extensionClass.getName(), extension);
            return extension;
        }
    }

    private <T> T createWithoutSpring(Class<T> extensionClass) {
        try {
            return extensionClass.getDeclaredConstructor().newInstance();
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private GenericApplicationContext getApplicationContext(Class<?> extensionClass) {
        PluginWrapper wrapper = pluginManager.whichPlugin(extensionClass);
        PmSpringPlugin plugin = (PmSpringPlugin) wrapper.getPlugin();
        return (GenericApplicationContext) plugin.getApplicationContext();
    }
}
