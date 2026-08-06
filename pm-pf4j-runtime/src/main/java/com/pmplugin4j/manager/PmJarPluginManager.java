package com.pmplugin4j.manager;

import com.pmplugin4j.descriptor.PmPropertiesPluginDescriptorFinder;
import java.nio.file.Path;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.JarPluginManager;
import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginLoader;
import org.pf4j.PluginRepository;

public class PmJarPluginManager extends JarPluginManager {

    private ConfigurationRepository configurationRepository;

    public PmJarPluginManager(Path pluginsRoot) {
        super(pluginsRoot);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new CompoundPluginDescriptorFinder().add(new PmPropertiesPluginDescriptorFinder())
            .add(new ManifestPluginDescriptorFinder());
    }

    @Override
    protected PluginRepository createPluginRepository() {
        return new PmJarPluginRepository(getPluginsRoots());
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return new PmJarPluginLoader(this);
    }

    protected ConfigurationRepository createConfigurationRepository() {
        Path configPath = getPluginsRoots().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No pluginsRoot configured"));

        return new PmPluginConfigurationRepository(configPath);
    }

    @Override
    protected void initialize() {
        super.initialize();
        this.resolveRecoveryStrategy = ResolveRecoveryStrategy.IGNORE_PLUGIN_AND_CONTINUE;
        this.configurationRepository = createConfigurationRepository();
    }

}
