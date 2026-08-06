package com.pmplugin4j.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PmPluginConfigurationRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesReadsAndDeletesPluginConfiguration() {
        PmPluginConfigurationRepository repository = new PmPluginConfigurationRepository(temporaryDirectory);

        repository.save("com.example.plugin", Map.of("enabled", "true", "limit", "10"));

        assertEquals(Map.of("enabled", "true", "limit", "10"), repository.get("com.example.plugin"));
        assertTrue(repository.delete("com.example.plugin"));
        assertTrue(repository.get("com.example.plugin").isEmpty());
        assertFalse(repository.delete("com.example.plugin"));
    }
}
