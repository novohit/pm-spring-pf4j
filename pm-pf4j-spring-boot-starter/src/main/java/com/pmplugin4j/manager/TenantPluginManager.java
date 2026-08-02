package com.pmplugin4j.manager;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.config.TenantPluginConfig;
import com.pmplugin4j.factory.PmPluginFactory;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 租户级别插件管理器
 *
 * 基于PF4J实现，增加租户级别的插件控制
 */
@Component
public class TenantPluginManager {

    private static final Logger log = LoggerFactory.getLogger(TenantPluginManager.class);

    private final PluginProperties pluginProperties;
    private final ApplicationContext applicationContext;
    private final List<PluginResourceRegistrar> externalRegistrars = new ArrayList<>();
    private volatile boolean registrarsFrozen;
    private JarPluginManager pf4jManager;
    private PmPluginFactory pluginFactory;
    private final Map<String, PluginWrapper> loadedPlugins = new ConcurrentHashMap<>();
    private String currentTenantId;

    public TenantPluginManager(PluginProperties pluginProperties, ApplicationContext applicationContext) {
        this.pluginProperties = pluginProperties;
        this.applicationContext = applicationContext;
    }

    public synchronized void addExternalRegistrar(PluginResourceRegistrar registrar) {
        Objects.requireNonNull(registrar, "registrar must not be null");
        if (registrarsFrozen) {
            throw new IllegalStateException("Registrar must be added before plugin loading begins");
        }
        if (externalRegistrars.contains(registrar)) {
            log.warn("Duplicate registrar ignored: {}", registrar.getClass().getName());
            return;
        }
        externalRegistrars.add(registrar);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        currentTenantId = pluginProperties.getCurrentTenant();
        if (currentTenantId == null) {
            log.warn("No current tenant configured, skipping plugin loading");
            return;
        }

        log.info("Initializing plugins for tenant: {}", currentTenantId);

        // 创建PF4J PluginManager，使用自定义PluginFactory
        registrarsFrozen = true;
        pluginFactory = new PmPluginFactory(applicationContext, pluginProperties, List.copyOf(externalRegistrars));
        PmJarPluginManager.setPendingFactory(pluginFactory);
        pf4jManager = new PmJarPluginManager(pluginFactory);

        // 加载当前租户的插件
        loadTenantPlugins(currentTenantId);
    }

    /**
     * 加载指定租户的插件
     */
    private void loadTenantPlugins(String tenantId) {
        TenantPluginConfig config = pluginProperties.getTenants().get(tenantId);
        if (config == null) {
            log.warn("No plugin config for tenant: {}", tenantId);
            return;
        }

        // 解析启用的插件列表（支持profile引用）
        List<String> enabledPlugins = resolveEnabledPlugins(config);
        if (enabledPlugins.isEmpty()) {
            log.info("No plugins enabled for tenant: {}", tenantId);
            return;
        }

        log.info("Loading plugins for tenant {}: {}", tenantId, enabledPlugins);

        // 扫描插件目录
        Path pluginsDir = Paths.get(pluginProperties.getDirectory());
        if (!Files.exists(pluginsDir)) {
            log.warn("Plugins directory not found: {}", pluginsDir);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jarPath : stream) {
                String pluginId = extractPluginId(jarPath);
                if (pluginId != null && enabledPlugins.contains(pluginId)) {
                    loadAndStartPlugin(jarPath, pluginId);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan plugins directory", e);
        }
    }

    /**
     * 解析启用的插件列表（支持profile引用）
     */
    private List<String> resolveEnabledPlugins(TenantPluginConfig config) {
        // 优先使用直接配置的enabled-plugins
        List<String> enabledPlugins = config.getEnabledPlugins();
        if (enabledPlugins != null && !enabledPlugins.isEmpty()) {
            return enabledPlugins;
        }

        // 如果配置了profile，从profile中获取
        String profile = config.getProfile();
        if (profile != null && !profile.isEmpty()) {
            List<String> profilePlugins = pluginProperties.getProfiles().get(profile);
            if (profilePlugins != null) {
                log.info("Resolved profile '{}' for tenant: {}", profile, profilePlugins);
                return profilePlugins;
            } else {
                log.warn("Profile '{}' not found", profile);
            }
        }

        return Collections.emptyList();
    }

    /**
     * 从JAR文件中提取插件ID
     */
    private String extractPluginId(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            // 尝试从MANIFEST.MF获取
            ZipEntry manifest = jarFile.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) {
                try (InputStream is = jarFile.getInputStream(manifest)) {
                    java.util.Properties props = new java.util.Properties();
                    props.load(is);
                    return props.getProperty("Plugin-Id");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract plugin id from: {}", jarPath, e);
        }
        return null;
    }

    /**
     * 加载并启动插件
     */
    public void loadAndStartPlugin(Path jarPath, String pluginId) {
        try {
            log.info("Loading plugin: {} from {}", pluginId, jarPath);

            String loadedPluginId = pf4jManager.loadPlugin(jarPath);
            pf4jManager.startPlugin(loadedPluginId);

            PluginWrapper wrapper = pf4jManager.getPlugin(loadedPluginId);
            loadedPlugins.put(pluginId, wrapper);

            log.info("Plugin loaded and started: {}", pluginId);

        } catch (Exception e) {
            log.error("Failed to load plugin: {}", pluginId, e);
        }
    }

    /**
     * 卸载插件
     */
    public void unloadPlugin(String pluginId) {
        PluginWrapper wrapper = loadedPlugins.get(pluginId);
        if (wrapper == null) {
            log.warn("Plugin not loaded: {}", pluginId);
            return;
        }

        try {
            pf4jManager.stopPlugin(pluginId);
            pf4jManager.unloadPlugin(pluginId);
            loadedPlugins.remove(pluginId);
            log.info("Plugin unloaded: {}", pluginId);
        } catch (Exception e) {
            log.error("Failed to unload plugin: {}", pluginId, e);
        }
    }

    /** Stops and starts a plugin, rebuilding its Spring context. */
    public PluginState restartPlugin(String pluginId) {
        PluginWrapper wrapper = loadedPlugins.get(pluginId);
        if (wrapper == null) {
            return PluginState.UNLOADED;
        }
        pf4jManager.stopPlugin(pluginId);
        return pf4jManager.startPlugin(pluginId);
    }

    /**
     * 获取当前租户可用的扩展
     */
    public <T> List<T> getExtensions(Class<T> type) {
        if (pf4jManager == null) {
            return Collections.emptyList();
        }
        return pf4jManager.getExtensions(type);
    }

    /**
     * 获取指定插件的扩展
     */
    public <T> List<T> getExtensions(Class<T> type, String pluginId) {
        if (pf4jManager == null) {
            return Collections.emptyList();
        }
        return pf4jManager.getExtensions(type, pluginId);
    }

    /**
     * 获取已加载的插件列表
     */
    public Collection<PluginWrapper> getLoadedPlugins() {
        return Collections.unmodifiableCollection(loadedPlugins.values());
    }

    /**
     * 获取插件状态
     */
    public PluginState getPluginState(String pluginId) {
        PluginWrapper wrapper = loadedPlugins.get(pluginId);
        if (wrapper == null) {
            return null;
        }
        return wrapper.getPluginState();
    }

    /**
     * 检查插件是否已加载
     */
    public boolean isPluginLoaded(String pluginId) {
        return loadedPlugins.containsKey(pluginId);
    }

    /**
     * 获取当前租户ID
     */
    public String getCurrentTenantId() {
        return currentTenantId;
    }

    /**
     * 获取PF4J PluginManager
     */
    public JarPluginManager getPf4jManager() {
        return pf4jManager;
    }
}
