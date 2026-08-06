package com.pmplugin4j.config;

import com.pmplugin4j.event.DefaultEventBus;
import com.pmplugin4j.event.EventBus;
import com.pmplugin4j.manager.PmPluginBootstrap;
import com.pmplugin4j.manager.PmPluginManager;
import com.pmplugin4j.manager.TenantPluginSelector;
import com.pmplugin4j.registry.DefaultSpiRegistry;
import com.pmplugin4j.registry.SpiRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PF4J插件框架自动配置
 */
@Configuration
@ConditionalOnProperty(prefix = "pm.pf4j", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PluginProperties.class)
public class Pf4jPluginAutoConfiguration {

    @Bean
    public PmPluginManager pmPluginManager(PluginProperties pluginProperties) {
        Path pluginsRoot = Paths.get(pluginProperties.getDirectory()).toAbsolutePath().normalize();
        return new PmPluginManager(pluginsRoot, pluginProperties);
    }

    @Bean
    public TenantPluginSelector tenantPluginSelector(PluginProperties pluginProperties) {
        return new TenantPluginSelector(pluginProperties);
    }

    @Bean
    public PmPluginBootstrap pmPluginBootstrap(PmPluginManager pluginManager,
            TenantPluginSelector tenantPluginSelector) {
        return new PmPluginBootstrap(pluginManager, tenantPluginSelector);
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
