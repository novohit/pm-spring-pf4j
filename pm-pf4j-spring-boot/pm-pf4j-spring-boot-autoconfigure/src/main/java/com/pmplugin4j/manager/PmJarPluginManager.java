package com.pmplugin4j.manager;

import org.pf4j.JarPluginManager;
import org.pf4j.PluginFactory;

public class PmJarPluginManager extends JarPluginManager {

    private static PluginFactory pendingFactory;

    public PmJarPluginManager(PluginFactory pluginFactory) {
        super();
    }

    public static void setPendingFactory(PluginFactory factory) {
        pendingFactory = factory;
    }

    @Override
    protected PluginFactory createPluginFactory() {
        if (pendingFactory != null) {
            PluginFactory f = pendingFactory;
            pendingFactory = null;
            return f;
        }
        return super.createPluginFactory();
    }
}
