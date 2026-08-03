package com.pmplugin4j.security;

import com.pmplugin4j.PmPluginAuthRegistry;
import com.pmplugin4j.PmPluginFilterRegistry;
import com.pmplugin4j.core.DefaultPluginAnonymousPathRegistry;
import com.pmplugin4j.core.PluginAnonymousPathRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the web-stack-independent plugin authentication infrastructure. */
@Configuration
@AutoConfigureAfter(name = "com.pmplugin4j.config.Pf4jPluginAutoConfiguration")
@EnableConfigurationProperties(PluginFilterConfigProperties.class)
public class PmSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PmPluginAuthRegistry.class)
    public PmPluginAuthRegistry pluginAuthRegistry() {
        return new DefaultPluginAuthRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(PluginAnonymousPathRegistry.class)
    public DefaultPluginAnonymousPathRegistry pluginAnonymousPathRegistry() {
        return new DefaultPluginAnonymousPathRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(PluginRouteRegistry.class)
    public PluginRouteRegistry pluginRouteRegistry() {
        return new DefaultPluginRouteRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(AuthRegistrar.class)
    public AuthRegistrar authRegistrar() {
        return new AuthRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean(PluginAuthenticatedPathRegistry.class)
    public PluginAuthenticatedPathRegistry pluginAuthenticatedPathRegistry() {
        return new DefaultPluginAuthenticatedPathRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationStrategy.class)
    public AuthenticationStrategy authenticationStrategy() {
        return new AtLeastOneSuccessfulStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(PmPluginFilterRegistry.class)
    public PmPluginFilterRegistry pluginFilterRegistry() {
        return new PmPluginFilterRegistry();
    }
}
