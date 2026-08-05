/*
 * Copyright (c) 2025 grejeff.
 */

package com.pmplugin4j.webflux;

import com.pmplugin4j.core.PluginAnonymousPathRegistrar;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class PmPluginWebFluxConfig {

    @Bean("pluginWebFluxRequestMappingHandlerMapping")
    public PmPluginWebFluxRequestMappingHandlerMapping webFluxRequestMappingHandlerMapping(
            PluginAnonymousPathRegistrar anonymousPathRegistrar) {
        RequestedContentTypeResolver requestedContentTypeResolver = new RequestedContentTypeResolverBuilder().build();
        PmPluginWebFluxRequestMappingHandlerMapping webFluxHandlerMapping = new PmPluginWebFluxRequestMappingHandlerMapping();
        webFluxHandlerMapping.setContentTypeResolver(requestedContentTypeResolver);
        webFluxHandlerMapping.setOrder(-1);
        webFluxHandlerMapping.setUseCaseSensitiveMatch(false);
        webFluxHandlerMapping.setAnonymousPathRegistry(anonymousPathRegistrar);
        return webFluxHandlerMapping;
    }

    @Bean
    public PmPluginWebFluxRouterFunctionRegistry pluginWebFluxRouterFunctionRegistry() {
        return new PmPluginWebFluxDefaultRouterFunctionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    WebFluxRegistrations webFluxRegistrations() {
        return new WebFluxRegistrations() {
            @Override
            public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
                return new PmPluginWebFluxSecureRequestMappingHandlerAdapter();
            }
        };
    }
}
