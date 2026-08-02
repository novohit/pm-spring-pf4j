package com.pmplugin4j.registry;

import java.util.Collection;

/**
 * SPI注册表接口
 */
public interface SpiRegistry {

    /**
     * 注册扩展实现
     */
    <T> void registerExtension(Class<T> extensionType, T implementation, String tenantId);

    /**
     * 注册带优先级的扩展实现
     */
    <T> void registerExtension(Class<T> extensionType, T implementation, String tenantId, int priority);

    /**
     * 获取扩展实现（单个）
     */
    <T> T getExtension(Class<T> extensionType, String tenantId);

    /**
     * 获取所有扩展实现
     */
    <T> Collection<T> getExtensions(Class<T> extensionType, String tenantId);

    /**
     * 注销扩展实现
     */
    <T> void unregisterExtension(Class<T> extensionType, String tenantId);

    /**
     * 注销所有扩展实现
     */
    void unregisterAll(String pluginId);

    /**
     * 检查是否有扩展实现
     */
    boolean hasExtension(Class<?> extensionType, String tenantId);
}
