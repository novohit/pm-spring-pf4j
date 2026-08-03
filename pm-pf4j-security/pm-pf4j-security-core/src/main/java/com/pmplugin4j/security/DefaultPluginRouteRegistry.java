package com.pmplugin4j.security;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe default plugin route registry. */
public final class DefaultPluginRouteRegistry implements PluginRouteRegistry {

    private final Map<String, Map<String, Set<String>>> routes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<String>>> authenticatedRoutes = new ConcurrentHashMap<>();

    @Override
    public void register(String pluginId, String httpMethod, String pathPattern, boolean pluginAuthenticated) {
        add(routes, pluginId, httpMethod, pathPattern);
        if (pluginAuthenticated) {
            add(authenticatedRoutes, pluginId, httpMethod, pathPattern);
        }
    }

    @Override
    public Map<String, Set<String>> getPluginPaths(String pluginId) {
        return snapshot(routes.get(pluginId));
    }

    @Override
    public Map<String, Set<String>> getPluginAuthenticatedPaths(String pluginId) {
        return snapshot(authenticatedRoutes.get(pluginId));
    }

    @Override
    public void unregister(String pluginId) {
        routes.remove(pluginId);
        authenticatedRoutes.remove(pluginId);
    }

    private static void add(Map<String, Map<String, Set<String>>> index, String pluginId, String httpMethod,
            String pathPattern) {
        index.computeIfAbsent(pluginId, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(httpMethod.toUpperCase(), ignored -> ConcurrentHashMap.newKeySet())
            .add(pathPattern);
    }

    private static Map<String, Set<String>> snapshot(Map<String, Set<String>> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Set<String>> result = new ConcurrentHashMap<>();
        source.forEach((method, patterns) -> result.put(method, Set.copyOf(new LinkedHashSet<>(patterns))));
        return Map.copyOf(result);
    }
}
