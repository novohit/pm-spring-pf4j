package com.pmplugin4j.controller;

import com.pmplugin4j.manager.TenantPluginManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * PF4J插件管理控制器
 */
@Tag(name = "插件管理")
@RestController
@RequestMapping("/api/plugin")
@RequiredArgsConstructor
public class Pf4jPluginManagementController {

    private final TenantPluginManager tenantPluginManager;

    @Operation(summary = "获取所有插件列表")
    @GetMapping("/list")
    public List<PluginInfo> listPlugins() {
        List<PluginInfo> result = new ArrayList<>();

        for (PluginWrapper wrapper : tenantPluginManager.getLoadedPlugins()) {
            PluginInfo info = new PluginInfo();
            info.setId(wrapper.getPluginId());
            info.setName(wrapper.getDescriptor().getPluginDescription());
            info.setVersion(wrapper.getDescriptor().getVersion());
            info.setStatus(wrapper.getPluginState().toString());
            info.setPluginClass(wrapper.getPlugin().getClass().getName());
            result.add(info);
        }

        return result;
    }

    @Operation(summary = "获取插件详情")
    @GetMapping("/{pluginId}")
    public PluginInfo getPlugin(@PathVariable String pluginId) {
        PluginWrapper wrapper = tenantPluginManager.getLoadedPlugins().stream()
            .filter(p -> p.getPluginId().equals(pluginId))
            .findFirst()
            .orElse(null);

        if (wrapper == null) {
            return null;
        }

        PluginInfo info = new PluginInfo();
        info.setId(wrapper.getPluginId());
        info.setName(wrapper.getDescriptor().getPluginDescription());
        info.setVersion(wrapper.getDescriptor().getVersion());
        info.setStatus(wrapper.getPluginState().toString());
        info.setPluginClass(wrapper.getPlugin().getClass().getName());
        return info;
    }

    @Operation(summary = "上传并安装插件")
    @PostMapping(value = "/install", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PluginOperationResult installPlugin(@RequestParam("file") MultipartFile file) {
        PluginOperationResult result = new PluginOperationResult();
        try {
            // 保存文件到临时目录
            Path tempFile = Files.createTempFile("plugin-", ".jar");
            file.transferTo(tempFile.toFile());

            // 加载插件
            tenantPluginManager.loadAndStartPlugin(tempFile, file.getOriginalFilename());
            result.setSuccess(true);
            result.setMessage("插件安装成功");
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("插件安装失败: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "卸载插件")
    @DeleteMapping("/{pluginId}")
    public PluginOperationResult uninstallPlugin(@PathVariable String pluginId) {
        PluginOperationResult result = new PluginOperationResult();
        try {
            tenantPluginManager.unloadPlugin(pluginId);
            result.setSuccess(true);
            result.setMessage("插件卸载成功");
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("插件卸载失败: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "获取插件状态")
    @GetMapping("/{pluginId}/status")
    public Map<String, Object> getPluginStatus(@PathVariable String pluginId) {
        Map<String, Object> result = new HashMap<>();
        result.put("pluginId", pluginId);
        result.put("loaded", tenantPluginManager.isPluginLoaded(pluginId));

        PluginState state = tenantPluginManager.getPluginState(pluginId);
        result.put("state", state != null ? state.toString() : "UNLOADED");

        return result;
    }

    @Operation(summary = "获取当前租户ID")
    @GetMapping("/tenant")
    public Map<String, String> getCurrentTenant() {
        Map<String, String> result = new HashMap<>();
        result.put("tenantId", tenantPluginManager.getCurrentTenantId());
        return result;
    }

    @Data
    public static class PluginInfo {
        private String id;
        private String name;
        private String version;
        private String status;
        private String pluginClass;
    }

    @Data
    public static class PluginOperationResult {
        private boolean success;
        private String message;
    }
}
