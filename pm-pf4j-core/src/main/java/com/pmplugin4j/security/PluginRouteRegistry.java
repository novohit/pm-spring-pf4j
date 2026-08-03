package com.pmplugin4j.security;

import java.util.Map;
import java.util.Set;

/** Stores plugin-owned routes used by optional request-processing integrations. */
public interface PluginRouteRegistry {

    void register(String pluginId, String httpMethod, String pathPattern, boolean pluginAuthenticated);

    Map<String, Set<String>> getPluginPaths(String pluginId);

    Map<String, Set<String>> getPluginAuthenticatedPaths(String pluginId);

    void unregister(String pluginId);
}
