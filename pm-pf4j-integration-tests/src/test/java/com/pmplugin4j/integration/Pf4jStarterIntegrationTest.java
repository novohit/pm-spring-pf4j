package com.pmplugin4j.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplugin4j.config.Pf4jPluginAutoConfiguration;
import com.pmplugin4j.event.EventBus;
import com.pmplugin4j.hotreload.PmPluginHotReloadManager;
import com.pmplugin4j.manager.PmPluginBootstrap;
import com.pmplugin4j.manager.PmPluginManager;
import com.pmplugin4j.manager.PmPluginService;
import com.pmplugin4j.mybatis.PluginMybatisRegistrar;
import com.pmplugin4j.registry.SpiRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class Pf4jStarterIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(Pf4jPluginAutoConfiguration.class))
        .withPropertyValues("pm.pf4j.hot-reload=manual");

    @Test
    void starterProvidesHostRuntimeBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PmPluginManager.class);
            assertThat(context).hasSingleBean(PmPluginBootstrap.class);
            assertThat(context).hasSingleBean(PmPluginService.class);
            assertThat(context).hasSingleBean(PmPluginHotReloadManager.class);
            assertThat(context).hasSingleBean(SpiRegistry.class);
            assertThat(context).hasSingleBean(EventBus.class);
        });
    }

    @Test
    void starterAutoConfiguresDefaultMybatisIntegration() {
        new ApplicationContextRunner().withUserConfiguration(TestApplication.class).run(context -> {
            assertThat(context).hasSingleBean(PmPluginManager.class);
            assertThat(context).hasSingleBean(PluginMybatisRegistrar.class);
        });
    }

    @Test
    void starterCanBeDisabledWithConfigurationProperty() {
        contextRunner.withPropertyValues("pm.pf4j.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(PmPluginManager.class);
            assertThat(context).doesNotHaveBean(PmPluginBootstrap.class);
            assertThat(context).doesNotHaveBean(PmPluginService.class);
            assertThat(context).doesNotHaveBean(PmPluginHotReloadManager.class);
            assertThat(context).doesNotHaveBean(SpiRegistry.class);
            assertThat(context).doesNotHaveBean(EventBus.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
