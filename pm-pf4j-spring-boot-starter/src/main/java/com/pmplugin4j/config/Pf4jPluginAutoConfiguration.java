package com.pmplugin4j.config;

import com.pmplugin4j.event.DefaultEventBus;
import com.pmplugin4j.event.EventBus;
import com.pmplugin4j.hotreload.PmPluginHotReloadManager;
import com.pmplugin4j.manager.PmPluginBootstrap;
import com.pmplugin4j.manager.PmPluginManager;
import com.pmplugin4j.manager.PmPluginService;
import com.pmplugin4j.manager.TenantPluginSelector;
import com.pmplugin4j.registry.DefaultSpiRegistry;
import com.pmplugin4j.registry.SpiRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * PF4J插件框架自动配置
 */
@Configuration
@ConditionalOnProperty(prefix = "pm.pf4j", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PluginProperties.class)
public class Pf4jPluginAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Pf4jPluginAutoConfiguration.class);
    private final Environment environment;

    public Pf4jPluginAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public PmPluginManager pmPluginManager(PluginProperties pluginProperties) {
        Path pluginsRoot = resolvePluginsRoot(pluginProperties);
        validatePluginDirectory(pluginsRoot);
        log.info("plugin directory path: {}", pluginsRoot.toAbsolutePath());
        return new PmPluginManager(pluginsRoot, pluginProperties);
    }

    @Bean
    public TenantPluginSelector tenantPluginSelector(PluginProperties pluginProperties) {
        return new TenantPluginSelector(pluginProperties);
    }

    @Bean
    public PmPluginService pmPluginService(PmPluginManager pluginManager) {
        return new PmPluginService(pluginManager);
    }

    @Bean
    public PmPluginHotReloadManager pmPluginHotReloadManager(PmPluginService pluginService,
            PmPluginManager pluginManager, PluginProperties properties) {
        try {
            Path pluginsDir = pluginManager.getPluginsRoots().get(0);
            return new PmPluginHotReloadManager(pluginService, pluginsDir, properties);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to create PmPluginHotReloadManager", exception);
        }
    }

    @Bean
    public PmPluginBootstrap pmPluginBootstrap(ApplicationContext applicationContext, PmPluginManager pluginManager,
            TenantPluginSelector tenantPluginSelector, PmPluginHotReloadManager hotReloadManager) {
        return new PmPluginBootstrap(applicationContext, pluginManager, tenantPluginSelector, hotReloadManager);
    }

    @Bean
    public SpiRegistry spiRegistry() {
        return new DefaultSpiRegistry();
    }

    @Bean
    public EventBus eventBus() {
        return new DefaultEventBus();
    }

    private Path resolvePluginsRoot(PluginProperties properties) {
        String configuredDirectory = properties.getDirectory();
        if (configuredDirectory != null && !configuredDirectory.isBlank()
                && !PluginProperties.DEFAULT_PLUGIN_DIR.equals(configuredDirectory)) {
            Path pluginsDir = Paths.get(configuredDirectory).toAbsolutePath().normalize();
            if (!Files.isDirectory(pluginsDir)) {
                throw new IllegalStateException("Configured plugin dir does not exist: " + pluginsDir);
            }
            return pluginsDir;
        }
        if (environment.acceptsProfiles(Profiles.of("dev | debug"))) {
            String currentDirectory = System.getProperty("user.dir");
            log.info("current working directory: {}", currentDirectory);
            return Paths.get(currentDirectory, PluginProperties.DEFAULT_PLUGIN_DIR);
        }
        ApplicationHome applicationHome = new ApplicationHome(getClass());
        return applicationHome.getDir().toPath().resolve(PluginProperties.DEFAULT_PLUGIN_DIR);
    }

    private static void validatePluginDirectory(Path pluginsDir) {
        if (!Files.exists(pluginsDir)) {
            try {
                Files.createDirectories(pluginsDir);
                log.info("Plugin directory does not exist, auto-created: {}", pluginsDir.toAbsolutePath());
            } catch (IOException exception) {
                log.info("Failed to create plugin directory: {}", pluginsDir.toAbsolutePath());
                throw new RuntimeException("Failed to create plugin directory", exception);
            }
        }
        if (!Files.isDirectory(pluginsDir)) {
            throw new IllegalArgumentException("Specified path is not a directory: " + pluginsDir.toAbsolutePath());
        }
        checkDuplicateSubdirectoriesIgnoreCase(pluginsDir);
    }

    /** Checks first-level plugin directory names because plugin IDs are case-insensitive operational identifiers. */
    private static void checkDuplicateSubdirectoriesIgnoreCase(Path pluginsDir) {
        Set<String> directoryNamesLower = new HashSet<>();
        Set<String> originalNames = new TreeSet<>();
        Set<String> duplicateOriginalNames = new TreeSet<>();
        try (Stream<Path> paths = Files.list(pluginsDir)) {
            paths.filter(Files::isDirectory).map(Path::getFileName).forEach(directory -> {
                String originalName = directory.toString();
                if (!directoryNamesLower.add(originalName.toLowerCase())) {
                    duplicateOriginalNames.add(originalName);
                    originalNames.stream()
                        .filter(existing -> existing.equalsIgnoreCase(originalName))
                        .forEach(duplicateOriginalNames::add);
                }
                originalNames.add(originalName);
            });
        } catch (IOException exception) {
            throw new RuntimeException("Error reading plugin directory: " + pluginsDir.toAbsolutePath(), exception);
        }
        if (!duplicateOriginalNames.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate subdirectory names found (case-insensitive) in plugin directory: %s (directory path: %s)",
                    duplicateOriginalNames, pluginsDir.toAbsolutePath()));
        }
    }
}
