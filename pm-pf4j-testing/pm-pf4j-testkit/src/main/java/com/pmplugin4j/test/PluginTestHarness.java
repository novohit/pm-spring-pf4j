package com.pmplugin4j.test;

import java.nio.file.Path;

/** Shared entry point for plugin integration-test fixtures. */
public final class PluginTestHarness {

    private final Path pluginsDirectory;

    public PluginTestHarness(Path pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory;
    }

    public Path pluginsDirectory() {
        return pluginsDirectory;
    }
}
