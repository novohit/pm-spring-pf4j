package com.agileboot.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件框架配置属性
 *
 * 配置来源：application-plugin.yml
 * 职责：管理租户-插件映射和全局插件配置
 */
@ConfigurationProperties(prefix = "agileboot.plugin")
public class PluginProperties {

    /** 是否启用插件系统 */
    private boolean enabled = true;

    /** 插件文件存储目录 */
    private String directory = "plugins";

    /** 当前实例的租户ID（启动时确定） */
    private String currentTenant;

    /** 是否允许运行时安装插件 */
    private boolean allowRuntimeInstall = false;

    /** 是否允许运行时卸载插件 */
    private boolean allowRuntimeUninstall = false;

    /** 插件组模板（多对一：多个租户共享一个模板） */
    private Map<String, List<String>> profiles = new HashMap<>();

    /** 租户插件配置 */
    private Map<String, TenantPluginConfig> tenants = new HashMap<>();

    // ========== getters/setters ==========

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDirectory() { return directory; }
    public void setDirectory(String directory) { this.directory = directory; }

    public String getCurrentTenant() { return currentTenant; }
    public void setCurrentTenant(String currentTenant) { this.currentTenant = currentTenant; }

    public boolean isAllowRuntimeInstall() { return allowRuntimeInstall; }
    public void setAllowRuntimeInstall(boolean allowRuntimeInstall) { this.allowRuntimeInstall = allowRuntimeInstall; }

    public boolean isAllowRuntimeUninstall() { return allowRuntimeUninstall; }
    public void setAllowRuntimeUninstall(boolean allowRuntimeUninstall) { this.allowRuntimeUninstall = allowRuntimeUninstall; }

    public Map<String, List<String>> getProfiles() { return profiles; }
    public void setProfiles(Map<String, List<String>> profiles) { this.profiles = profiles; }

    public Map<String, TenantPluginConfig> getTenants() { return tenants; }
    public void setTenants(Map<String, TenantPluginConfig> tenants) { this.tenants = tenants; }

    // ========== 便捷方法 ==========

    /**
     * 获取当前租户的启用插件列表
     * 优先从租户配置获取，如果没有则从profile获取
     */
    public List<String> getEnabledPlugins() {
        if (currentTenant == null) {
            return Collections.emptyList();
        }

        TenantPluginConfig tenantConfig = tenants.get(currentTenant);
        if (tenantConfig == null) {
            return Collections.emptyList();
        }

        // 如果租户直接配置了enabled-plugins
        if (tenantConfig.getEnabledPlugins() != null && !tenantConfig.getEnabledPlugins().isEmpty()) {
            return tenantConfig.getEnabledPlugins();
        }

        // 如果租户引用了profile
        if (tenantConfig.getProfile() != null) {
            List<String> profilePlugins = profiles.get(tenantConfig.getProfile());
            return profilePlugins != null ? profilePlugins : Collections.emptyList();
        }

        return Collections.emptyList();
    }

    /**
     * 获取指定租户的配置
     */
    public TenantPluginConfig getTenantConfig(String tenantId) {
        return tenants.get(tenantId);
    }

    /**
     * 检查指定插件是否在指定租户中启用
     */
    public boolean isPluginEnabledForTenant(String pluginId, String tenantId) {
        TenantPluginConfig tenantConfig = tenants.get(tenantId);
        if (tenantConfig == null) {
            return false;
        }

        // 如果租户直接配置了enabled-plugins
        if (tenantConfig.getEnabledPlugins() != null) {
            return tenantConfig.getEnabledPlugins().contains(pluginId);
        }

        // 如果租户引用了profile
        if (tenantConfig.getProfile() != null) {
            List<String> profilePlugins = profiles.get(tenantConfig.getProfile());
            return profilePlugins != null && profilePlugins.contains(pluginId);
        }

        return false;
    }

    /**
     * 获取指定租户中指定插件的配置
     */
    public TenantPluginConfig.PluginInstanceConfig getPluginConfig(String pluginId, String tenantId) {
        TenantPluginConfig tenantConfig = tenants.get(tenantId);
        if (tenantConfig == null) {
            return new TenantPluginConfig.PluginInstanceConfig();
        }
        return tenantConfig.getPluginConfig(pluginId);
    }
}
