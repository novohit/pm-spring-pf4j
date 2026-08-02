package com.pmplugin4j.factory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

import java.beans.Introspector;

public class PluginBeanNameGenerator extends AnnotationBeanNameGenerator {

    private final String pluginPrefix;

    public PluginBeanNameGenerator(String pluginId) {
        int lastDot = pluginId.lastIndexOf('.');
        this.pluginPrefix = (lastDot >= 0 ? pluginId.substring(lastDot + 1) : pluginId) + ".";
    }

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        String beanName = super.generateBeanName(definition, registry);
        if (beanName.startsWith(pluginPrefix)) {
            return beanName;
        }
        return pluginPrefix + beanName;
    }

    @Override
    protected String buildDefaultBeanName(BeanDefinition definition) {
        String beanClassName = definition.getBeanClassName();
        if (beanClassName == null) {
            return super.buildDefaultBeanName(definition);
        }
        String shortName = ClassUtils.getShortName(beanClassName);
        String baseName = Introspector.decapitalize(shortName);
        return pluginPrefix + baseName;
    }

    private static class ClassUtils {
        static String getShortName(String className) {
            int lastDot = className.lastIndexOf('.');
            return lastDot >= 0 ? className.substring(lastDot + 1) : className;
        }
    }
}
