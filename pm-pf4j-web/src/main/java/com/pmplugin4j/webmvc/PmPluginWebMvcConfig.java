package com.pmplugin4j.webmvc;

import com.pmplugin4j.core.PluginAnonymousPathRegistrar;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

/** Configures the plugin-owned MVC handler mapping. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PmPluginWebMvcConfig {

    @Bean("pluginRequestMappingHandlerMapping")
    public PmPluginRequestMappingHandlerMapping pluginRequestMappingHandlerMapping(
            PluginAnonymousPathRegistrar anonymousPathRegistrar) {
        PmPluginRequestMappingHandlerMapping handlerMapping = new PmPluginRequestMappingHandlerMapping();
        handlerMapping.setOrder(-1);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        pathMatcher.setCaseSensitive(false);
        handlerMapping.setPathMatcher(pathMatcher);
        handlerMapping.setPatternParser(null);
        handlerMapping.setAnonymousPathRegistry(anonymousPathRegistrar);
        return handlerMapping;
    }
}
