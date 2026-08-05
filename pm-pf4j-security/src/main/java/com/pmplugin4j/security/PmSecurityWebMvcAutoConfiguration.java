package com.pmplugin4j.security;

import com.pmplugin4j.PmPluginAuthRegistry;
import com.pmplugin4j.PmPluginFilterPosition;
import com.pmplugin4j.PmPluginFilterRegistry;
import com.pmplugin4j.core.PluginAnonymousPathRegistry;
import com.pmplugin4j.security.servlet.PluginCompositeFilter;
import com.pmplugin4j.security.servlet.PluginDelegatingAuthFilter;
import com.pmplugin4j.security.servlet.PluginSecurityConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures Servlet security filters for plugin authentication. */
@Configuration
@AutoConfigureAfter(PmSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PmSecurityWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ServletFilterRegistrar.class)
    public ServletFilterRegistrar servletFilterRegistrar(PmPluginFilterRegistry registry,
            PluginFilterConfigProperties properties) {
        return new ServletFilterRegistrar(registry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PluginDelegatingAuthFilter.class)
    public PluginDelegatingAuthFilter pluginDelegatingAuthFilter(PmPluginAuthRegistry registry,
            PluginAnonymousPathRegistry anonymousPaths, PluginAuthenticatedPathRegistry authenticatedPaths,
            AuthenticationStrategy strategy, ApplicationEventPublisher publisher) {
        return new PluginDelegatingAuthFilter(registry, anonymousPaths, authenticatedPaths, strategy, publisher);
    }

    @Bean("pluginFirstFilter")
    @ConditionalOnMissingBean(name = "pluginFirstFilter")
    public PluginCompositeFilter pluginFirstFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.FIRST, publisher);
    }

    @Bean("pluginSessionRestoreFilter")
    @ConditionalOnMissingBean(name = "pluginSessionRestoreFilter")
    public PluginCompositeFilter pluginSessionRestoreFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.SESSION_RESTORE, publisher);
    }

    @Bean("pluginFormLoginFilter")
    @ConditionalOnMissingBean(name = "pluginFormLoginFilter")
    public PluginCompositeFilter pluginFormLoginFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.FORM_LOGIN, publisher);
    }

    @Bean("pluginAnonymousFilter")
    @ConditionalOnMissingBean(name = "pluginAnonymousFilter")
    public PluginCompositeFilter pluginAnonymousFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.ANONYMOUS, publisher);
    }

    @Bean("pluginPreAuthorizeFilter")
    @ConditionalOnMissingBean(name = "pluginPreAuthorizeFilter")
    public PluginCompositeFilter pluginPreAuthorizeFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.PRE_AUTHORIZE, publisher);
    }

    @Bean("pluginLastFilter")
    @ConditionalOnMissingBean(name = "pluginLastFilter")
    public PluginCompositeFilter pluginLastFilter(PmPluginFilterRegistry registry,
            ApplicationEventPublisher publisher) {
        return composite(registry, PmPluginFilterPosition.LAST, publisher);
    }

    @Bean
    @ConditionalOnMissingBean(PluginSecurityConfigurer.class)
    public PluginSecurityConfigurer pluginSecurityConfigurer() {
        return new PluginSecurityConfigurer();
    }

    private PluginCompositeFilter composite(PmPluginFilterRegistry registry, PmPluginFilterPosition position,
            ApplicationEventPublisher publisher) {
        return new PluginCompositeFilter(registry, position, publisher);
    }
}
