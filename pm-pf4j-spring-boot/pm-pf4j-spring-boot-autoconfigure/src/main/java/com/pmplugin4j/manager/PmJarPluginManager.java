package com.pmplugin4j.manager;

import com.pmplugin4j.descriptor.PmPluginDescriptor;
import com.pmplugin4j.descriptor.PmPropertiesPluginDescriptorFinder;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import org.pf4j.CompoundPluginDescriptorFinder;
import org.pf4j.JarPluginManager;
import org.pf4j.ManifestPluginDescriptorFinder;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRepository;
import org.pf4j.PluginWrapper;

public class PmJarPluginManager extends JarPluginManager {

    private static final ThreadLocal<PluginFactory> PENDING_FACTORY = new ThreadLocal<>();

    public PmJarPluginManager(Path pluginsRoot, PluginFactory pluginFactory) {
        super(prepareInitialization(pluginsRoot, pluginFactory));
    }

    private static Path prepareInitialization(Path pluginsRoot, PluginFactory pluginFactory) {
        PENDING_FACTORY.set(pluginFactory);
        return pluginsRoot;
    }

    @Override
    protected PluginFactory createPluginFactory() {
        PluginFactory pluginFactory = PENDING_FACTORY.get();
        if (pluginFactory != null) {
            PENDING_FACTORY.remove();
            return pluginFactory;
        }
        return super.createPluginFactory();
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

    /** Starts the tenant-enabled plugins in descriptor order; PF4J starts required dependencies recursively. */
    public void startPlugins(Collection<String> enabledPluginIds) {
        getResolvedPlugins().stream()
            .filter(wrapper -> enabledPluginIds.contains(wrapper.getPluginId()))
            .sorted(Comparator.comparingInt(this::getOrder))
            .map(PluginWrapper::getPluginId)
            .forEach(this::startPlugin);
    }

    private int getOrder(PluginWrapper wrapper) {
        if (wrapper.getDescriptor() instanceof PmPluginDescriptor descriptor) {
            return descriptor.getOrder();
        }
        return 100000;
    }
}
