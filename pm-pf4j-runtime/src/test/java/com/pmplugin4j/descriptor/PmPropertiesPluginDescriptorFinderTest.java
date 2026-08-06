package com.pmplugin4j.descriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginDescriptor;

class PmPropertiesPluginDescriptorFinderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsPluginPropertiesAndStartupOrderFromJar() throws IOException {
        Path pluginJar = temporaryDirectory.resolve("sample-plugin-1.0.0.jar");
        String descriptorContent = """
                plugin.id=com.example.sample
                plugin.class=com.example.sample.SamplePlugin
                plugin.version=1.0.0
                plugin.provider=example
                plugin.dependencies=
                plugin.order=42
                """;
        try (OutputStream outputStream = Files.newOutputStream(pluginJar);
                JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
            jarOutputStream.putNextEntry(new JarEntry("plugin.properties"));
            jarOutputStream.write(descriptorContent.getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }

        PluginDescriptor descriptor = new PmPropertiesPluginDescriptorFinder().find(pluginJar);

        PmPluginDescriptor pmDescriptor = assertInstanceOf(PmPluginDescriptor.class, descriptor);
        assertEquals("com.example.sample", pmDescriptor.getPluginId());
        assertEquals("com.example.sample.SamplePlugin", pmDescriptor.getPluginClass());
        assertEquals("1.0.0", pmDescriptor.getVersion());
        assertEquals(42, pmDescriptor.getOrder());
    }
}
