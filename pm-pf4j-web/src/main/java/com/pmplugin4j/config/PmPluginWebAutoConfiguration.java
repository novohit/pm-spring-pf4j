package com.pmplugin4j.config;

import com.pmplugin4j.core.DefaultPluginAnonymousPathRegistry;
import com.pmplugin4j.core.PluginAnonymousPathRegistry;
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
}
