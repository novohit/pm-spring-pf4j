package com.pmplugin4j.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认SPI注册表实现
 */
public class DefaultSpiRegistry implements SpiRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultSpiRegistry.class);

    private final Map<String, Map<String, ExtensionEntry<?>>> extensions = new ConcurrentHashMap<>();

    @Override
    public <T> void registerExtension(Class<T> extensionType, T implementation, String tenantId) {
        registerExtension(extensionType, implementation, tenantId, 100);
    }

    @Override
    public <T> void registerExtension(Class<T> extensionType, T implementation, String tenantId, int priority) {
        String key = extensionType.getName();
        String tenantKey = tenantId != null ? tenantId : "*";

        extensions.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
            .put(tenantKey, new ExtensionEntry<>(implementation, priority, tenantId));

        log.debug("Registered extension: {} for tenant: {} with priority: {}",
            extensionType.getSimpleName(), tenantKey, priority);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getExtension(Class<T> extensionType, String tenantId) {
        String key = extensionType.getName();
        Map<String, ExtensionEntry<?>> tenantExtensions = extensions.get(key);

        if (tenantExtensions == null || tenantExtensions.isEmpty()) {
            return null;
        }

        // 优先查找租户特定的扩展
        if (tenantId != null) {
            ExtensionEntry<?> entry = tenantExtensions.get(tenantId);
            if (entry != null) {
                return (T) entry.implementation;
            }
        }

        // 回退到通配符扩展
        ExtensionEntry<?> entry = tenantExtensions.get("*");
        return entry != null ? (T) entry.implementation : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<T> getExtensions(Class<T> extensionType, String tenantId) {
        String key = extensionType.getName();
        Map<String, ExtensionEntry<?>> tenantExtensions = extensions.get(key);

        if (tenantExtensions == null || tenantExtensions.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> result = new ArrayList<>();

        // 添加租户特定的扩展
        if (tenantId != null) {
            ExtensionEntry<?> entry = tenantExtensions.get(tenantId);
            if (entry != null) {
                result.add((T) entry.implementation);
            }
        }

        // 添加通配符扩展
        ExtensionEntry<?> entry = tenantExtensions.get("*");
        if (entry != null) {
            result.add((T) entry.implementation);
        }

        // 按优先级排序
        result.sort((a, b) -> {
            int priorityA = getPriority(tenantExtensions, a, tenantId);
            int priorityB = getPriority(tenantExtensions, b, tenantId);
            return Integer.compare(priorityA, priorityB);
        });

        return result;
    }

    @Override
    public <T> void unregisterExtension(Class<T> extensionType, String tenantId) {
        String key = extensionType.getName();
        String tenantKey = tenantId != null ? tenantId : "*";

        Map<String, ExtensionEntry<?>> tenantExtensions = extensions.get(key);
        if (tenantExtensions != null) {
            tenantExtensions.remove(tenantKey);
            log.debug("Unregistered extension: {} for tenant: {}", extensionType.getSimpleName(), tenantKey);
        }
    }

    @Override
    public void unregisterAll(String pluginId) {
        // TODO: 实现按插件ID注销所有扩展
        log.debug("Unregistering all extensions for plugin: {}", pluginId);
    }

    @Override
    public boolean hasExtension(Class<?> extensionType, String tenantId) {
        String key = extensionType.getName();
        Map<String, ExtensionEntry<?>> tenantExtensions = extensions.get(key);

        if (tenantExtensions == null || tenantExtensions.isEmpty()) {
            return false;
        }

        String tenantKey = tenantId != null ? tenantId : "*";
        return tenantExtensions.containsKey(tenantKey) || tenantExtensions.containsKey("*");
    }

    private <T> int getPriority(Map<String, ExtensionEntry<?>> tenantExtensions, T implementation, String tenantId) {
        // 简化实现，实际应维护实现到优先级的映射
        return 100;
    }

    private static class ExtensionEntry<T> {
        final T implementation;
        final int priority;
        final String tenantId;

        ExtensionEntry(T implementation, int priority, String tenantId) {
            this.implementation = implementation;
            this.priority = priority;
            this.tenantId = tenantId;
        }
    }
}
