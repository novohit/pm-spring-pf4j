package com.pmplugin4j.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PmJarPluginRepositoryTest {

    @TempDir
    Path pluginRoot;

    @Test
    void selectsLatestJarFromEachPluginDirectory() throws IOException {
        Path firstPlugin = Files.createDirectory(pluginRoot.resolve("com.example.first"));
        Files.createFile(firstPlugin.resolve("com.example.first-1.0.0.jar"));
        Path latest = Files.createFile(firstPlugin.resolve("com.example.first-1.1.0.jar"));
        Path secondPlugin = Files.createDirectory(pluginRoot.resolve("com.example.second"));
        Path second = Files.createFile(secondPlugin.resolve("com.example.second-2.0.0.jar"));

        List<Path> selected = new PmJarPluginRepository(List.of(pluginRoot)).findLatestJarFilesInRoot(pluginRoot)
            .map(File::toPath)
            .toList();

        assertEquals(2, selected.size());
        assertTrue(selected.contains(latest));
        assertTrue(selected.contains(second));
    }

    @Test
    void prefersReleaseOverPreReleaseAndIgnoresUnmatchedJarNames() throws IOException {
        Path pluginDirectory = Files.createDirectory(pluginRoot.resolve("com.example.plugin"));
        Files.createFile(pluginDirectory.resolve("com.example.plugin-2.0.0-alpha.1.jar"));
        Path release = Files.createFile(pluginDirectory.resolve("com.example.plugin-2.0.0.jar"));
        Files.createFile(pluginDirectory.resolve("another-plugin-9.0.0.jar"));
        Files.createFile(pluginDirectory.resolve("com.example.plugin-no-version.jar"));

        List<Path> selected = new PmJarPluginRepository(List.of(pluginRoot)).findLatestJarFilesInRoot(pluginRoot)
            .map(File::toPath)
            .toList();

        assertEquals(List.of(release), selected);
    }

    @Test
    void deletesCompletePluginDirectory() throws IOException {
        Path pluginDirectory = Files.createDirectory(pluginRoot.resolve("com.example.plugin"));
        Path nestedDirectory = Files.createDirectory(pluginDirectory.resolve("resources"));
        Files.createFile(pluginDirectory.resolve("com.example.plugin-1.0.0.jar"));
        Files.createFile(nestedDirectory.resolve("configuration.yml"));
        PmJarPluginRepository repository = new PmJarPluginRepository(List.of(pluginRoot));

        assertTrue(repository.deletePluginPath(pluginDirectory));
        assertFalse(Files.exists(pluginDirectory));
        assertFalse(repository.deletePluginPath(pluginDirectory));
    }

    @Test
    void returnsEveryConflictingJarSoTheRepositoryCanReportIt() throws IOException {
        Path firstDirectory = Files.createDirectory(pluginRoot.resolve("com.example.plugin-1.0.0"));
        Path secondDirectory = Files.createDirectory(pluginRoot.resolve("com.example.plugin-2.0.0"));
        Files.createFile(firstDirectory.resolve("com.example.plugin-3.0.0.jar"));
        Files.createFile(secondDirectory.resolve("com.example.plugin-3.0.0.jar"));

        List<Path> selected = new PmJarPluginRepository(List.of(pluginRoot)).findLatestJarFilesInRoot(pluginRoot)
            .map(File::toPath)
            .toList();

        assertEquals(2, selected.size());
    }
}
