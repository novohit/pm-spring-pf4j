/*
 * Copyright (c) 2025 grejeff.
 */

package com.pmplugin4j.security;

import com.pmplugin4j.PmPluginFilterPosition;
import com.pmplugin4j.PmPluginFilterRegistry;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import com.pmplugin4j.security.servlet.*;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Map;

/**
 * Scans plugin MVC filter extensions and registers them into {@link PmPluginFilterRegistry}, subject to host
 * configuration.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletFilterRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ServletFilterRegistrar.class);

    private final PmPluginFilterRegistry registry;
    private final PluginFilterConfigProperties config;

    public ServletFilterRegistrar(PmPluginFilterRegistry registry, PluginFilterConfigProperties config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH, PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        return 910;
    }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        if (!config.getFilter().isEnabled())
            return;

        String pluginId = pluginCtx.getId();
        Set<PmPluginFilterPosition> allowed = config.getFilter().getAllowedPositions();
        PluginFilterConfigProperties.PluginSecurityConfig pluginConfig = config.getPlugins().get(pluginId);
        Set<PmPluginFilterPosition> pluginPositions = pluginConfig != null && pluginConfig.getFilter() != null
                ? pluginConfig.getFilter().getAllowedPositions() : allowed;

        register(pluginCtx, pluginId, FirstFilterExtension.class, PmPluginFilterPosition.FIRST, allowed,
                pluginPositions);
        register(pluginCtx, pluginId, SessionRestoreFilterExtension.class, PmPluginFilterPosition.SESSION_RESTORE,
                allowed, pluginPositions);
        register(pluginCtx, pluginId, FormLoginFilterExtension.class, PmPluginFilterPosition.FORM_LOGIN, allowed,
                pluginPositions);
        register(pluginCtx, pluginId, AnonymousFilterExtension.class, PmPluginFilterPosition.ANONYMOUS, allowed,
                pluginPositions);
        register(pluginCtx, pluginId, PreAuthorizeFilterExtension.class, PmPluginFilterPosition.PRE_AUTHORIZE, allowed,
                pluginPositions);
        register(pluginCtx, pluginId, LastFilterExtension.class, PmPluginFilterPosition.LAST, allowed, pluginPositions);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void register(AnnotationConfigApplicationContext pluginCtx, String pluginId, Class extensionClass,
            PmPluginFilterPosition pos, Set<PmPluginFilterPosition> allowed,
            Set<PmPluginFilterPosition> pluginPositions) {
        if (!allowed.contains(pos) || !pluginPositions.contains(pos)) {
            return;
        }
        Map<String, ?> beans = pluginCtx.getBeansOfType(extensionClass);
        for (Object bean : beans.values()) {
            int order = 0;
            try {
                order = (int) bean.getClass().getMethod("getOrder").invoke(bean);
            } catch (Exception ignored) {
            }
            try {
                Object filter = bean.getClass().getMethod("getFilter").invoke(bean);
                registry.registerServletFilter(pluginId, pos, (Filter) filter, order);
                log.info("[Plugin: {}] Registered {} filter at {} (order={})", pluginId,
                        bean.getClass().getSimpleName(), pos, order);
            } catch (Exception e) {
                log.error("[Plugin: {}] Failed to get filter from {}: {}", pluginId, bean.getClass().getSimpleName(),
                        e.getMessage());
            }
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        registry.unregister(pluginCtx.getId());
    }
}
