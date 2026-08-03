/*
 * Copyright (c) 2025 grejeff.
 */

package com.pmplugin4j;

import jakarta.servlet.Filter;
import org.springframework.core.Ordered;
import org.springframework.web.server.WebFilter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry for plugin filter extensions.
 * <p>
 * Separate storage for MVC ({@code Filter}) and WebFlux ({@code WebFilter}), indexed by {@link PmPluginFilterPosition}.
 * Reads on the request hot path are lock-free via {@code CopyOnWriteArrayList}.
 */
public class PmPluginFilterRegistry {

    private final Map<PmPluginFilterPosition, List<Filter>> servletFilters = new ConcurrentHashMap<>();

    private final Map<PmPluginFilterPosition, List<WebFilter>> webFilters = new ConcurrentHashMap<>();

    private final Map<String, Set<PmPluginFilterPosition>> pluginIndex = new ConcurrentHashMap<>();

    /** pluginId → (position → filters) — reverse index for precise unregistration */
    private final Map<String, Map<PmPluginFilterPosition, List<Filter>>> pluginServletFilters = new ConcurrentHashMap<>();

    /** pluginId → (position → webFilters) — reverse index for precise unregistration */
    private final Map<String, Map<PmPluginFilterPosition, List<WebFilter>>> pluginWebFilters = new ConcurrentHashMap<>();

    /** filter instance → pluginId — for event correlation */
    private final Map<Object, String> filterOwnerIndex = new ConcurrentHashMap<>();

    public PmPluginFilterRegistry() {
        for (PmPluginFilterPosition pos : PmPluginFilterPosition.values()) {
            servletFilters.put(pos, new CopyOnWriteArrayList<>());
            webFilters.put(pos, new CopyOnWriteArrayList<>());
        }
    }

    /** Register a servlet filter at the given position, sorted by order. */
    public void registerServletFilter(String pluginId, PmPluginFilterPosition pos, Filter filter, int order) {
        List<Filter> list = servletFilters.get(pos);
        if (list == null)
            return;
        for (int i = 0; i < list.size(); i++) {
            if (getFilterOrder(list.get(i)) > order) {
                list.add(i, filter);
                recordPluginFilter(pluginId, pos, filter);
                return;
            }
        }
        list.add(filter);
        recordPluginFilter(pluginId, pos, filter);
    }

    /** Register a WebFlux web filter at the given position, sorted by order. */
    public void registerWebFilter(String pluginId, PmPluginFilterPosition pos, WebFilter webFilter, int order) {
        List<WebFilter> list = webFilters.get(pos);
        if (list == null)
            return;
        for (int i = 0; i < list.size(); i++) {
            if (getWebFilterOrder(list.get(i)) > order) {
                list.add(i, webFilter);
                recordPluginWebFilter(pluginId, pos, webFilter);
                return;
            }
        }
        list.add(webFilter);
        recordPluginWebFilter(pluginId, pos, webFilter);
    }

    private void recordPluginFilter(String pluginId, PmPluginFilterPosition pos, Filter filter) {
        pluginIndex.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        pluginServletFilters.computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(pos, k -> new CopyOnWriteArrayList<>())
            .add(filter);
        filterOwnerIndex.put(filter, pluginId);
    }

    private void recordPluginWebFilter(String pluginId, PmPluginFilterPosition pos, WebFilter webFilter) {
        pluginIndex.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        pluginWebFilters.computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(pos, k -> new CopyOnWriteArrayList<>())
            .add(webFilter);
        filterOwnerIndex.put(webFilter, pluginId);
    }

    /** Look up which plugin registered a filter. */
    public String getFilterPluginId(Object filter) {
        return filterOwnerIndex.get(filter);
    }

    /** Remove all filters registered by a plugin. */
    public void unregister(String pluginId) {
        // Clean up servlet filters via reverse index
        Map<PmPluginFilterPosition, List<Filter>> servletMap = pluginServletFilters.remove(pluginId);
        if (servletMap != null) {
            for (Map.Entry<PmPluginFilterPosition, List<Filter>> entry : servletMap.entrySet()) {
                List<Filter> sfList = servletFilters.get(entry.getKey());
                if (sfList != null) {
                    entry.getValue().forEach(f -> {
                        sfList.remove(f);
                        filterOwnerIndex.remove(f);
                    });
                }
            }
        }

        // Clean up web filters via reverse index
        Map<PmPluginFilterPosition, List<WebFilter>> webMap = pluginWebFilters.remove(pluginId);
        if (webMap != null) {
            for (Map.Entry<PmPluginFilterPosition, List<WebFilter>> entry : webMap.entrySet()) {
                List<WebFilter> wfList = webFilters.get(entry.getKey());
                if (wfList != null) {
                    entry.getValue().forEach(wf -> {
                        wfList.remove(wf);
                        filterOwnerIndex.remove(wf);
                    });
                }
            }
        }

        pluginIndex.remove(pluginId);
    }

    /** Get all servlet filters at a position (read-safe). */
    public List<Filter> getFilters(PmPluginFilterPosition pos) {
        List<Filter> list = servletFilters.get(pos);
        return list != null ? list : List.of();
    }

    /** Get all web filters at a position (read-safe). */
    public List<WebFilter> getWebFilters(PmPluginFilterPosition pos) {
        List<WebFilter> list = webFilters.get(pos);
        return list != null ? list : List.of();
    }

    private int getFilterOrder(Filter f) {
        if (f instanceof Ordered o)
            return o.getOrder();
        return 0;
    }

    private int getWebFilterOrder(WebFilter wf) {
        if (wf instanceof Ordered o)
            return o.getOrder();
        return 0;
    }
}
