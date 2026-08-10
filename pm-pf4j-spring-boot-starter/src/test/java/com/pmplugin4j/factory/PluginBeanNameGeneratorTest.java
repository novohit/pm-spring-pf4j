package com.pmplugin4j.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.stereotype.Service;

class PluginBeanNameGeneratorTest {

    @Test
    void prefixesDefaultAndExplicitBeanNames() {
        PluginBeanNameGenerator generator = new PluginBeanNameGenerator("example");
        SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

        assertEquals("example.pluginBeanNameGeneratorTest.SampleService",
                generator.generateBeanName(new AnnotatedGenericBeanDefinition(SampleService.class), registry));
        assertEquals("example.customService",
                generator.generateBeanName(new AnnotatedGenericBeanDefinition(ExplicitService.class), registry));
    }

    @Test
    void usesSpringNamingForReservedPluginPrefix() {
        PluginBeanNameGenerator generator = new PluginBeanNameGenerator("plugin");
        assertEquals("pluginBeanNameGeneratorTest.SampleService", generator.generateBeanName(
                new AnnotatedGenericBeanDefinition(SampleService.class), new SimpleBeanDefinitionRegistry()));
    }

    @Service
    static class SampleService {
    }

    @Service("customService")
    static class ExplicitService {
    }
}
