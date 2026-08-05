package com.pmplugin4j.security;

import com.pmplugin4j.PmPluginAuthRegistry;
import com.pmplugin4j.PmPluginFilterPosition;
import com.pmplugin4j.PmPluginFilterRegistry;
import com.pmplugin4j.core.PluginAnonymousPathRegistry;
import com.pmplugin4j.security.reactive.PluginCompositeWebFilter;
import com.pmplugin4j.security.reactive.PluginDelegatingAuthWebFilter;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Configures reactive security filters for plugin authentication. */
@Configuration
@AutoConfigureAfter(PmSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class PmSecurityWebFluxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveFilterRegistrar.class)
    public ReactiveFilterRegistrar reactiveFilterRegistrar(PmPluginFilterRegistry registry,
            PluginFilterConfigProperties properties) {
        return new ReactiveFilterRegistrar(registry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PluginDelegatingAuthWebFilter.class)
    public PluginDelegatingAuthWebFilter pluginDelegatingAuthWebFilter(PmPluginAuthRegistry registry,
            PluginAnonymousPathRegistry anonymousPaths, PluginAuthenticatedPathRegistry authenticatedPaths,
            AuthenticationStrategy strategy, ApplicationEventPublisher publisher) {
        return new PluginDelegatingAuthWebFilter(registry, anonymousPaths, authenticatedPaths, strategy, publisher);
    }

    @Bean("pluginFirstWebFilter")
    @ConditionalOnMissingBean(name = "pluginFirstWebFilter")
    public PluginCompositeWebFilter pluginFirstWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.FIRST, Ordered.HIGHEST_PRECEDENCE + 5, publisher);
    }

    @Bean("pluginSessionRestoreWebFilter")
    @ConditionalOnMissingBean(name = "pluginSessionRestoreWebFilter")
    public PluginCompositeWebFilter pluginSessionRestoreWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.SESSION_RESTORE, Ordered.HIGHEST_PRECEDENCE + 15, publisher);
    }

    @Bean("pluginFormLoginWebFilter")
    @ConditionalOnMissingBean(name = "pluginFormLoginWebFilter")
    public PluginCompositeWebFilter pluginFormLoginWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.FORM_LOGIN, Ordered.HIGHEST_PRECEDENCE + 25, publisher);
    }

    @Bean("pluginAnonymousWebFilter")
    @ConditionalOnMissingBean(name = "pluginAnonymousWebFilter")
    public PluginCompositeWebFilter pluginAnonymousWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.ANONYMOUS, Ordered.HIGHEST_PRECEDENCE + 35, publisher);
    }

    @Bean("pluginPreAuthorizeWebFilter")
    @ConditionalOnMissingBean(name = "pluginPreAuthorizeWebFilter")
    public PluginCompositeWebFilter pluginPreAuthorizeWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.PRE_AUTHORIZE, Ordered.HIGHEST_PRECEDENCE + 45, publisher);
    }

    @Bean("pluginLastWebFilter")
    @ConditionalOnMissingBean(name = "pluginLastWebFilter")
    public PluginCompositeWebFilter pluginLastWebFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.LAST, Ordered.HIGHEST_PRECEDENCE + 55, publisher);
    }

    private PluginCompositeWebFilter composite(PmPluginFilterRegistry registry, PmPluginFilterPosition position,
            int order, ApplicationEventPublisher publisher) {
        return new PluginCompositeWebFilter(registry, position, order, publisher);
    }
}
