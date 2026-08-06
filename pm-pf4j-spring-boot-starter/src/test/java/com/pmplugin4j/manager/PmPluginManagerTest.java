package com.pmplugin4j.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginState;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PmPluginManagerTest {

    @TempDir
    Path pluginsRoot;

    @Test
    void freezesExternalRegistrarsWhenPluginLoadingBegins() {
        PluginProperties properties = new PluginProperties();
        PmPluginManager manager = new PmPluginManager(pluginsRoot, properties);
        PluginResourceRegistrar registrar = new PluginResourceRegistrar() {
        };

        manager.addExternalRegistrar(registrar);
        manager.addExternalRegistrar(registrar);
        assertEquals(1, manager.getExternalRegistrars().size());

        manager.loadPlugins();

        assertThrows(IllegalStateException.class, () -> manager.addExternalRegistrar(new PluginResourceRegistrar() {
        }));
    }

    @Test
    void returnsUnloadedForUnknownPluginAndRejectsMissingReloadDirectory() {
        PmPluginManager manager = new PmPluginManager(pluginsRoot, new PluginProperties());
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            manager.setApplicationContext(host);

            assertEquals(PluginState.UNLOADED, manager.startPlugin("missing"));
            assertEquals(PluginState.UNLOADED, manager.stopPlugin("missing"));
        }
    }
}
