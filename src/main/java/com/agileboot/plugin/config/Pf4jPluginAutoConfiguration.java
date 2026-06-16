package com.agileboot.plugin.config;

import com.agileboot.plugin.event.DefaultEventBus;
import com.agileboot.plugin.event.EventBus;
import com.agileboot.plugin.manager.TenantPluginManager;
import com.agileboot.plugin.registry.DefaultSpiRegistry;
import com.agileboot.plugin.registry.SpiRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PF4J插件框架自动配置
 */
@Configuration
@ConditionalOnProperty(prefix = "agileboot.plugin", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PluginProperties.class)
public class Pf4jPluginAutoConfiguration {

    @Bean
    public TenantPluginManager tenantPluginManager(PluginProperties pluginProperties, ApplicationContext applicationContext) {
        return new TenantPluginManager(pluginProperties, applicationContext);
    }

    @Bean
    public SpiRegistry spiRegistry() {
        return new DefaultSpiRegistry();
    }

    @Bean
    public EventBus eventBus() {
        return new DefaultEventBus();
    }
}
