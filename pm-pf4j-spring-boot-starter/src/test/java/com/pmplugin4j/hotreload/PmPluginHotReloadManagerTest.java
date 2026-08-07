package com.pmplugin4j.hotreload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.manager.PmPluginService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PmPluginHotReloadManagerTest {

    @TempDir
    private Path pluginsDirectory;

    @Test
    void manualModeDoesNotStartFileWatching() throws Exception {
        PmPluginService pluginService = mock(PmPluginService.class);
        PluginProperties properties = new PluginProperties();
        properties.setHotReload(PluginProperties.HotReload.MANUAL);
        PmPluginHotReloadManager manager = new PmPluginHotReloadManager(pluginService, pluginsDirectory, properties);

        manager.startWatching();
        manager.shutdown();

        verifyNoInteractions(pluginService);
    }

    @Test
    void watchModeReloadsPluginAfterJarIsCreated() throws Exception {
        PmPluginService pluginService = mock(PmPluginService.class);
        when(pluginService.unloadPlugin("example.plugin")).thenReturn(true);
        PluginProperties properties = new PluginProperties();
        PmPluginHotReloadManager manager = new PmPluginHotReloadManager(pluginService, pluginsDirectory, properties);

        try {
            manager.startWatching();
            Path pluginDirectory = Files.createDirectory(pluginsDirectory.resolve("example.plugin"));
            Files.createFile(pluginDirectory.resolve("example.plugin-1.0.0.jar"));

            verify(pluginService, timeout(6000)).unloadPlugin("example.plugin");
            verify(pluginService, timeout(1000)).installPlugin("example.plugin");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void unloadVetoPreventsAutomaticReinstall() throws Exception {
        PmPluginService pluginService = mock(PmPluginService.class);
        when(pluginService.unloadPlugin("veto.plugin")).thenReturn(false);
        Path pluginDirectory = Files.createDirectory(pluginsDirectory.resolve("veto.plugin"));
        PluginProperties properties = new PluginProperties();
        PmPluginHotReloadManager manager = new PmPluginHotReloadManager(pluginService, pluginsDirectory, properties);

        try {
            manager.startWatching();
            Files.createFile(pluginDirectory.resolve("veto.plugin-1.0.0.jar"));

            verify(pluginService, timeout(6000)).unloadPlugin("veto.plugin");
            verify(pluginService, never()).installPlugin("veto.plugin");
        } finally {
            manager.shutdown();
        }
    }
}
