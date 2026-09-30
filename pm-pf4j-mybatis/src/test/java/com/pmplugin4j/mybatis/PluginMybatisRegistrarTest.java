package com.pmplugin4j.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PluginMybatisRegistrarTest {

    @Test
    void delegatesInitializationAndCleanupToHostManager() {
        PluginMybatisSqlSessionManager manager = new PluginMybatisSqlSessionManager(
                new DriverManagerDataSource("jdbc:test"), new MybatisPlusInterceptor());
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
                AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext()) {
            host.getBeanFactory().registerSingleton("pluginMybatisSqlSessionManager", manager);
            host.refresh();
            plugin.setId("com.example.plugin");
            plugin.setParent(host);
            plugin.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("plugin", Map.of("pm.plugin.base-package", "com.example.plugin")));

            PluginMybatisRegistrar registrar = new PluginMybatisRegistrar();
            registrar.onBeforeContextRefresh(plugin);
            assertEquals(1, manager.trackedPluginCount());

            registrar.onBeforeContextClose(plugin);
            assertEquals(0, manager.trackedPluginCount());
        }
    }
}
