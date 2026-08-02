package com.pmplugin4j.lifecycle;

/** Marker for framework-owned registrars whose failures must stop plugin startup. */
public interface BuiltInPluginResourceRegistrar extends PluginResourceRegistrar {
}
