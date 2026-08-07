package com.pmplugin4j.hotreload;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.manager.PmPluginService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Watches versioned plugin directories and debounces automatic reload operations. */
public class PmPluginHotReloadManager {

    private static final Logger log = LoggerFactory.getLogger(PmPluginHotReloadManager.class);

    private final PmPluginService pluginService;
    private final Path pluginsDir;
    private final PluginProperties.HotReload mode;
    private final WatchService watchService;
    private final Map<String, WatchKey> watchKeys = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> debounceTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pm-hot-reload-debounce");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;

    public PmPluginHotReloadManager(PmPluginService pluginService, Path pluginsDir, PluginProperties properties)
            throws IOException {
        this.pluginService = pluginService;
        this.pluginsDir = pluginsDir;
        this.mode = properties.getHotReload();
        this.watchService = FileSystems.getDefault().newWatchService();
    }

    public void startWatching() {
        if (mode != PluginProperties.HotReload.WATCH) {
            log.info("[HotReload] Mode is manual, WatchService not started");
            return;
        }
        try {
            pluginsDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register plugins directory watch: " + pluginsDir, exception);
        }
        running = true;
        Thread watcher = new Thread(this::watchLoop, "pm-hot-reload-watcher");
        watcher.setDaemon(true);
        watcher.start();
        log.info("[HotReload] WatchService started, monitoring: {}", pluginsDir);

        // Register existing plugin directories
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(pluginsDir, Files::isDirectory)) {
            for (Path dir : dirs) {
                String pluginId = dir.getFileName().toString();
                registerPluginWatchKey(pluginId);
            }
        } catch (IOException exception) {
            log.error("[HotReload] Initial plugin directory scan failed: {}", exception.getMessage());
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(60, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException exception) {
                break;
            }
            if (key == null) {
                continue;
            }

            Path watchDir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                Path name = (Path) event.context();
                Path fullPath = watchDir.resolve(name);
                if (watchDir.equals(pluginsDir)) {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (Files.isDirectory(fullPath)) {
                            String pluginId = name.toString();
                            log.info("[HotReload] New plugin directory detected: {}", pluginId);
                            registerPluginWatchKey(pluginId);
                            scheduleDebounce(pluginId);
                        }
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        String pluginId = name.toString();
                        // Only react if this was a known plugin directory (not a stray JAR)
                        if (watchKeys.containsKey(pluginId)) {
                            log.info("[HotReload] Plugin directory deleted: {}", pluginId);
                            scheduleDebounce(pluginId);
                        } else {
                            log.debug("[HotReload] Ignored non-directory deletion in plugins/: {}", name);
                        }
                    }
                } else {
                    // Inside a plugin directory — JAR change
                    String pluginId = watchDir.getFileName().toString();
                    String fileName = name.toString();
                    if (fileName.endsWith(".jar")) {
                        log.info("[HotReload] File change detected: pluginId={}, kind={}", pluginId, kind);
                        scheduleDebounce(pluginId);
                    }
                }
            }
            key.reset();
        }
    }

    private void registerPluginWatchKey(String pluginId) {
        if (watchKeys.containsKey(pluginId)) {
            return;
        }
        Path pluginDir = pluginsDir.resolve(pluginId);
        if (!Files.isDirectory(pluginDir)) {
            return;
        }
        try {
            WatchKey key = pluginDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(pluginId, key);
        } catch (IOException exception) {
            log.error("[HotReload] Failed to register watch for plugin {}: {}", pluginId, exception.getMessage());
        }
    }

    private void cancelWatchKey(String pluginId) {
        WatchKey key = watchKeys.remove(pluginId);
        if (key != null) {
            key.cancel();
        }
    }

    private void scheduleDebounce(String pluginId) {
        ScheduledFuture<?> existing = debounceTimers.remove(pluginId);
        if (existing != null) {
            existing.cancel(false);
            log.debug("[HotReload] Reset debounce: {}", pluginId);
        }
        log.info("[HotReload] Debounce started (2s): {}", pluginId);
        ScheduledFuture<?> future = scheduler.schedule(() -> executeDebounced(pluginId), 2, TimeUnit.SECONDS);
        debounceTimers.put(pluginId, future);
    }

    private void executeDebounced(String pluginId) {
        debounceTimers.remove(pluginId);
        String reloadId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[HotReload] reloadId={}, pluginId={}, trigger: watch", reloadId, pluginId);

        Path pluginDir = pluginsDir.resolve(pluginId);
        if (!Files.isDirectory(pluginDir)) {
            log.info("[HotReload] Debounce expired, plugin dir removed, unloading: {}", pluginId);
            pluginService.unloadPlugin(pluginId);
            cancelWatchKey(pluginId);
            return;
        }

        boolean hasJar;
        try (Stream<Path> paths = Files.list(pluginDir)) {
            hasJar = paths.anyMatch(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(pluginId + "-") && name.endsWith(".jar");
            });
        } catch (IOException exception) {
            log.error("[HotReload] Failed to validate plugin directory: {}", pluginId, exception);
            return;
        }

        if (!hasJar) {
            log.info("[HotReload] No valid JAR found for {}: skipping", pluginId);
            return;
        }

        try {
            if (!pluginService.unloadPlugin(pluginId)) {
                log.warn("[HotReload] reloadId={} cancelled because plugin unload was vetoed", reloadId);
                return;
            }
            pluginService.installPlugin(pluginId);
            log.info("[HotReload] reloadId={} completed", reloadId);
        } catch (Exception exception) {
            log.error("[HotReload] reloadId={} failed: {}", reloadId, exception.getMessage(), exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[HotReload] WatchService shutting down...");
        running = false;
        scheduler.shutdown();
        watchKeys.values().forEach(WatchKey::cancel);
        watchKeys.clear();
        try {
            watchService.close();
        } catch (IOException exception) {
            log.warn("[HotReload] WatchService close error: {}", exception.getMessage());
        }
        log.info("[HotReload] WatchService shut down");
    }
}
