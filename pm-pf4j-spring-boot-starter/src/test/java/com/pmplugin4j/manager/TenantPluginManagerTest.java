package com.pmplugin4j.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TenantPluginManagerTest {

    @Test
    void programmaticRegistrarsFreezeWhenPluginLoadingBegins() {
        PluginProperties properties = new PluginProperties();
        properties.setCurrentTenant("test");
        PluginResourceRegistrar registrar = new PluginResourceRegistrar() {};

        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            TenantPluginManager manager = new TenantPluginManager(properties, host);
            assertDoesNotThrow(() -> manager.addExternalRegistrar(registrar));

            manager.init();

            assertThrows(
                    IllegalStateException.class,
                    () -> manager.addExternalRegistrar(new PluginResourceRegistrar() {}));
        }
    }
}
