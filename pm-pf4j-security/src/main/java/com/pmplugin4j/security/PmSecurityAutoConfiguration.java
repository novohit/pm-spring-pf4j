/*
 * Copyright (c) 2025 grejeff.
 */

package com.pmplugin4j.security;

import com.pmplugin4j.core.DefaultPluginAnonymousPathRegistry;
import com.pmplugin4j.core.PluginAnonymousPathRegistry;
import com.pmplugin4j.PmPluginAuthRegistry;
import com.pmplugin4j.PmPluginFilterPosition;
import com.pmplugin4j.PmPluginFilterRegistry;
import com.pmplugin4j.security.reactive.PluginCompositeWebFilter;
import com.pmplugin4j.security.reactive.PluginDelegatingAuthWebFilter;
import com.pmplugin4j.security.servlet.PluginCompositeFilter;
import com.pmplugin4j.security.servlet.PluginDelegatingAuthFilter;
import com.pmplugin4j.security.servlet.PluginSecurityConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for pm-pf4j-security. Activated automatically by Spring Boot when the module is on the classpath.
 * <p>
 * All beans are {@code @ConditionalOnMissingBean} — host application can override any of them by defining its own.
 */
@Configuration
@AutoConfigureAfter(name = "com.pmplugin4j.config.Pf4jPluginAutoConfiguration")
@EnableConfigurationProperties(PluginFilterConfigProperties.class)
public class PmSecurityAutoConfiguration {

    // ──── Core beans ──────────────────────────────────────────────

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
    @ConditionalOnMissingBean(ServletFilterRegistrar.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public ServletFilterRegistrar servletFilterRegistrar(PmPluginFilterRegistry registry,
            PluginFilterConfigProperties properties) {
        return new ServletFilterRegistrar(registry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveFilterRegistrar.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public ReactiveFilterRegistrar reactiveFilterRegistrar(PmPluginFilterRegistry registry,
            PluginFilterConfigProperties properties) {
        return new ReactiveFilterRegistrar(registry, properties);
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

    // ──── MVC Filter ─────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PluginDelegatingAuthFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginDelegatingAuthFilter pluginDelegatingAuthFilter(PmPluginAuthRegistry registry,
            PluginAnonymousPathRegistry anonymousPaths, PluginAuthenticatedPathRegistry authenticatedPaths,
            AuthenticationStrategy strategy, ApplicationEventPublisher eventPublisher) {
        return new PluginDelegatingAuthFilter(registry, anonymousPaths, authenticatedPaths, strategy, eventPublisher);
    }

    // ──── MVC Composite Filters (6 positions) ─────────────────────

    @Bean("pluginFirstFilter")
    @ConditionalOnMissingBean(name = "pluginFirstFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginFirstFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeFilter(registry, PmPluginFilterPosition.FIRST, eventPublisher);
    }

    @Bean("pluginSessionRestoreFilter")
    @ConditionalOnMissingBean(name = "pluginSessionRestoreFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginSessionRestoreFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeFilter(registry, PmPluginFilterPosition.SESSION_RESTORE, eventPublisher);
    }

    @Bean("pluginFormLoginFilter")
    @ConditionalOnMissingBean(name = "pluginFormLoginFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginFormLoginFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeFilter(registry, PmPluginFilterPosition.FORM_LOGIN, eventPublisher);
    }

    @Bean("pluginAnonymousFilter")
    @ConditionalOnMissingBean(name = "pluginAnonymousFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginAnonymousFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeFilter(registry, PmPluginFilterPosition.ANONYMOUS, eventPublisher);
    }

    @Bean("pluginPreAuthorizeFilter")
    @ConditionalOnMissingBean(name = "pluginPreAuthorizeFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginPreAuthorizeFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeFilter(registry, PmPluginFilterPosition.PRE_AUTHORIZE, eventPublisher);
    }

    @Bean("pluginLastFilter")
    @ConditionalOnMissingBean(name = "pluginLastFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginCompositeFilter pluginLastFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeFilter(registry, PmPluginFilterPosition.LAST, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public PluginSecurityConfigurer pluginSecurityConfigurer() {
        return new PluginSecurityConfigurer();
    }

    // ──── WebFlux Filter ──────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PluginDelegatingAuthWebFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginDelegatingAuthWebFilter pluginDelegatingAuthWebFilter(PmPluginAuthRegistry registry,
            PluginAnonymousPathRegistry anonymousPaths, PluginAuthenticatedPathRegistry authenticatedPaths,
            AuthenticationStrategy strategy, ApplicationEventPublisher eventPublisher) {
        return new PluginDelegatingAuthWebFilter(registry, anonymousPaths, authenticatedPaths, strategy,
                eventPublisher);
    }

    // ──── WebFlux Composite Filters (6 positions) ─────────────────

    @Bean("pluginFirstWebFilter")
    @ConditionalOnMissingBean(name = "pluginFirstWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginFirstWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeWebFilter(registry, PmPluginFilterPosition.FIRST, Ordered.HIGHEST_PRECEDENCE + 5,
                eventPublisher);
    }

    @Bean("pluginSessionRestoreWebFilter")
    @ConditionalOnMissingBean(name = "pluginSessionRestoreWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginSessionRestoreWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeWebFilter(registry, PmPluginFilterPosition.SESSION_RESTORE,
                Ordered.HIGHEST_PRECEDENCE + 15, eventPublisher);
    }

    @Bean("pluginFormLoginWebFilter")
    @ConditionalOnMissingBean(name = "pluginFormLoginWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginFormLoginWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeWebFilter(registry, PmPluginFilterPosition.FORM_LOGIN,
                Ordered.HIGHEST_PRECEDENCE + 25, eventPublisher);
    }

    @Bean("pluginAnonymousWebFilter")
    @ConditionalOnMissingBean(name = "pluginAnonymousWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginAnonymousWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeWebFilter(registry, PmPluginFilterPosition.ANONYMOUS, Ordered.HIGHEST_PRECEDENCE + 35,
                eventPublisher);
    }

    @Bean("pluginPreAuthorizeWebFilter")
    @ConditionalOnMissingBean(name = "pluginPreAuthorizeWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginPreAuthorizeWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeWebFilter(registry, PmPluginFilterPosition.PRE_AUTHORIZE,
                Ordered.HIGHEST_PRECEDENCE + 45, eventPublisher);
    }

    @Bean("pluginLastWebFilter")
    @ConditionalOnMissingBean(name = "pluginLastWebFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public PluginCompositeWebFilter pluginLastWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher eventPublisher) {
        return new PluginCompositeWebFilter(registry, PmPluginFilterPosition.LAST, Ordered.HIGHEST_PRECEDENCE + 55,
                eventPublisher);
    }
}
