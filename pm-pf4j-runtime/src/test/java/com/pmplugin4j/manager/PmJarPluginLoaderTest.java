package com.pmplugin4j.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.DefaultPluginManager;

class PmJarPluginLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsOnlyManifestDeclaredLibraries() throws IOException {
        Path pluginDirectory = Files.createDirectories(temporaryDirectory.resolve("example"));
        Path libraryDirectory = Files.createDirectories(pluginDirectory.resolve("lib"));
        final Path declaredLibrary = createJar(libraryDirectory.resolve("declared.jar"), null);
        createJar(libraryDirectory.resolve("ignored.jar"), null);
        Path pluginJar = createJar(pluginDirectory.resolve("example-1.0.0.jar"), "lib/declared.jar");

        DefaultPluginManager pluginManager = new DefaultPluginManager(temporaryDirectory);
        PmJarPluginLoader loader = new PmJarPluginLoader(pluginManager);
        DefaultPluginDescriptor descriptor = new DefaultPluginDescriptor("example", null, "example.Plugin", "1.0.0",
                null, null, null);

        PmPluginClassLoader classLoader = (PmPluginClassLoader) loader.loadPlugin(pluginJar, descriptor);
        Set<Path> loadedPaths = Arrays.stream(classLoader.getURLs()).map(this::toRealPath).collect(Collectors.toSet());

        assertEquals(2, loadedPaths.size());
        assertTrue(loadedPaths.contains(pluginJar.toRealPath()));
        assertTrue(loadedPaths.contains(declaredLibrary.toRealPath()));
    }

    private Path createJar(Path jarPath, String classPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (classPath != null) {
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        }
        try (OutputStream output = Files.newOutputStream(jarPath);
                JarOutputStream ignored = new JarOutputStream(output, manifest)) {
            // An empty JAR is sufficient for class-path loading verification.
        }
        return jarPath;
    }

    private Path toRealPath(URL url) {
        try {
            return Path.of(url.toURI()).toRealPath();
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
