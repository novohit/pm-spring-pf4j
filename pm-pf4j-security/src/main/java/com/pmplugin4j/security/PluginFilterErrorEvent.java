/*
 * Copyright (c) 2025 grejeff.
 */

package com.pmplugin4j.security;

import com.pmplugin4j.PmPluginFilterPosition;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code PluginCompositeFilter} / {@code PluginCompositeWebFilter} when a plugin filter throws an
 * exception during execution.
 * <p>
 * Host applications can listen via {@code @EventListener} for alerting, auditing, or circuit-breaking custom logic.
 */
public class PluginFilterErrorEvent extends ApplicationEvent {

    private final String pluginId;
    private final String filterClassName;
    private final PmPluginFilterPosition position;
    private final Exception exception;

    public PluginFilterErrorEvent(Object source, String pluginId, String filterClassName,
            PmPluginFilterPosition position, Exception exception) {
        super(source);
        this.pluginId = pluginId;
        this.filterClassName = filterClassName;
        this.position = position;
        this.exception = exception;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getFilterClassName() {
        return filterClassName;
    }

    public PmPluginFilterPosition getPosition() {
        return position;
    }

    public Exception getException() {
        return exception;
    }
}
