package com.pmplugin4j.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
