package com.pmplugin4j.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户-插件映射配置
 *
 * 职责：定义哪些租户使用哪些插件，以及每个插件的运行时配置
 * 配置来源：application-plugin.yml 或 数据库
 */
public class TenantPluginConfig {

    /** 租户ID */
    private String tenantId;

    /** 租户显示名称 */
    private String tenantName;

    /** 引用的插件组模板名称 */
    private String profile;

    /** 启用的插件ID列表（优先级高于profile） */
    private List<String> enabledPlugins;

    /** 每个插件的运行时配置 */
    private Map<String, PluginInstanceConfig> pluginConfigs = new HashMap<>();

    // ========== getters/setters ==========

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public List<String> getEnabledPlugins() { return enabledPlugins; }
    public void setEnabledPlugins(List<String> enabledPlugins) { this.enabledPlugins = enabledPlugins; }

    public Map<String, PluginInstanceConfig> getPluginConfigs() { return pluginConfigs; }
    public void setPluginConfigs(Map<String, PluginInstanceConfig> pluginConfigs) { this.pluginConfigs = pluginConfigs; }

    /**
     * 检查指定插件是否在此租户中启用
     */
    public boolean isPluginEnabled(String pluginId) {
        return enabledPlugins != null && enabledPlugins.contains(pluginId);
    }

    /**
     * 获取指定插件的配置
     */
    public PluginInstanceConfig getPluginConfig(String pluginId) {
        return pluginConfigs.getOrDefault(pluginId, new PluginInstanceConfig());
    }

    /**
     * 插件实例配置
     */
    public static class PluginInstanceConfig {
        /** 是否启用（默认true） */
        private boolean enabled = true;

        /** 自定义配置项 */
        private Map<String, Object> properties = new HashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) { this.properties = properties; }

        public Object get(String key) { return properties.get(key); }
        public Object get(String key, Object defaultValue) { return properties.getOrDefault(key, defaultValue); }
        public void set(String key, Object value) { properties.put(key, value); }
    }
}
