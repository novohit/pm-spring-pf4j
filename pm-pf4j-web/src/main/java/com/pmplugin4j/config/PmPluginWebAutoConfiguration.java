package com.pmplugin4j.config;

import com.pmplugin4j.core.DefaultPluginAnonymousPathRegistry;
import com.pmplugin4j.core.PluginAnonymousPathRegistry;
import com.pmplugin4j.lifecycle.AnonymousPathRegistrar;
import com.pmplugin4j.lifecycle.ControllerRegistrar;
import com.pmplugin4j.webmvc.PluginOpenApiRegistrar;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures shared infrastructure used by both plugin web stacks. */
@Configuration
public class PmPluginWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PluginAnonymousPathRegistry.class)
    public DefaultPluginAnonymousPathRegistry pluginAnonymousPathRegistry() {
        return new DefaultPluginAnonymousPathRegistry();
    }

    @Bean
    public ControllerRegistrar controllerRegistrar() {
        return new ControllerRegistrar();
    }

    @Bean
    public AnonymousPathRegistrar anonymousPathRegistrar() {
        return new AnonymousPathRegistrar();
    }

    @Bean
    public PluginOpenApiRegistrar pluginOpenApiRegistrar() {
        return new PluginOpenApiRegistrar();
    }
}
