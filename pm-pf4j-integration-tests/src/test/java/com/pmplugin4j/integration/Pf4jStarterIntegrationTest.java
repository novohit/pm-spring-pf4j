package com.pmplugin4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplugin4j.config.Pf4jPluginAutoConfiguration;
import com.pmplugin4j.event.EventBus;
import com.pmplugin4j.manager.TenantPluginManager;
import com.pmplugin4j.registry.SpiRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Pf4jStarterIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(Pf4jPluginAutoConfiguration.class));

    @Test
    void starterProvidesHostRuntimeBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TenantPluginManager.class);
            assertThat(context).hasSingleBean(SpiRegistry.class);
            assertThat(context).hasSingleBean(EventBus.class);
        });
    }

    @Test
    void starterCanBeDisabledWithConfigurationProperty() {
        contextRunner.withPropertyValues("pm.pf4j.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TenantPluginManager.class);
            assertThat(context).doesNotHaveBean(SpiRegistry.class);
            assertThat(context).doesNotHaveBean(EventBus.class);
        });
    }
}
