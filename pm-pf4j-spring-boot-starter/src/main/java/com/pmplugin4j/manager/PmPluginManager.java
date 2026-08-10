package com.pmplugin4j.manager;

import com.pmplugin4j.api.PmSpringPlugin;
import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.descriptor.PmPluginDescriptor;
import com.pmplugin4j.event.PmPluginAfterInstallEvent;
import com.pmplugin4j.event.PmPluginBeforeUnloadEvent;
import com.pmplugin4j.event.PmPluginDisabledEvent;
import com.pmplugin4j.event.PmPluginStartFailedEvent;
import com.pmplugin4j.event.PmPluginStartedEvent;
import com.pmplugin4j.event.PmPluginStartingError;
import com.pmplugin4j.factory.PmPluginFactory;
import com.pmplugin4j.hotreload.PluginHotReloadVetoException;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginDependency;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginStateEvent;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.lang.NonNull;

/** Spring-aware plugin manager with deterministic lifecycle and tenant-selected startup roots. */
public class PmPluginManager extends PmJarPluginManager implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(PmPluginManager.class);
    private final Map<String, PmPluginStartingError> startingErrors = new ConcurrentHashMap<>();
    final Map<String, ReentrantLock> pluginLocks = new ConcurrentHashMap<>();
    final Set<String> everStartedPluginIds = ConcurrentHashMap.newKeySet();
    private final List<PluginResourceRegistrar> externalRegistrars = new ArrayList<>();
    private final PluginProperties pluginProperties;
    private final Path pluginsRoot;
    private GenericApplicationContext mainApplicationContext;
    private boolean mainApplicationStarted;
    private boolean autoStartPlugin = true;
    private volatile boolean registrarsFrozen;

    public PmPluginManager(Path pluginsRoot, PluginProperties pluginProperties) {
        super(pluginsRoot);
        this.pluginsRoot = pluginsRoot;
        this.pluginProperties = pluginProperties;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        this.mainApplicationContext = (GenericApplicationContext) applicationContext;
    }

    public GenericApplicationContext getMainApplicationContext() {
        return mainApplicationContext;
    }

    public boolean isMainApplicationStarted() {
        return mainApplicationStarted;
    }

    public void setMainApplicationStarted(boolean mainApplicationStarted) {
        this.mainApplicationStarted = mainApplicationStarted;
    }

    public boolean isAutoStartPlugin() {
        return autoStartPlugin;
    }

    public PluginProperties getPluginProperties() {
        return pluginProperties;
    }

    public void addExternalRegistrar(PluginResourceRegistrar registrar) {
        Objects.requireNonNull(registrar);
        if (registrarsFrozen) {
            throw new IllegalStateException("Registrar must be added before loadPlugins() / startPlugins()");
        }
        if (externalRegistrars.contains(registrar)) {
            log.warn("[Registrar] Duplicate registrar ignored: {}", registrar.getClass().getSimpleName());
            return;
        }
        externalRegistrars.add(registrar);
    }

    public List<PluginResourceRegistrar> getExternalRegistrars() {
        return Collections.unmodifiableList(externalRegistrars);
    }

    @Override
    public void loadPlugins() {
        registrarsFrozen = true;
        super.loadPlugins();
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new PmPluginFactory();
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        return new PmPluginExtensionFactory(this);
    }

    @Override
    public void startPlugins() {
        startPlugins(getResolvedPlugins().stream().map(PluginWrapper::getPluginId).toList());
    }

    public void startPlugins(Collection<String> enabledPluginIds) {
        startingErrors.clear();
        long start = System.currentTimeMillis();
        getResolvedPlugins().stream()
            .filter(wrapper -> enabledPluginIds.contains(wrapper.getPluginId()))
            .sorted(Comparator.comparingInt(this::getOrder))
            .map(PluginWrapper::getPluginId)
            .forEach(this::startPlugin);

        long duration = System.currentTimeMillis() - start;
        List<String> startedPluginIds = startedPlugins.stream().map(PluginWrapper::getPluginId).toList();
        log.info("[PF4J] {} plugins are started in {}ms. {} failed. Started plugins: [{}]", startedPluginIds.size(),
                duration, startingErrors.size(), String.join(", ", startedPluginIds));
        if (!startingErrors.isEmpty()) {
            log.error("[PF4J] Plugin startup failures ({}):", startingErrors.size());
            logPluginErrors();
        }
    }

    @Override
    public PluginState startPlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper == null) {
            log.info("Plugin already unloaded or not found: {}", pluginId);
            return PluginState.UNLOADED;
        }
        PluginState previousState = wrapper.getPluginState();
        if (previousState.isStarted()) {
            log.info("Already started plugin '{}'", pluginId);
            return PluginState.STARTED;
        }
        if (!resolvedPlugins.contains(wrapper)) {
            log.warn("Cannot start an unresolved plugin '{}'", getPluginLabel(wrapper.getDescriptor()));
            return previousState;
        }
        for (PluginDependency dependency : wrapper.getDescriptor().getDependencies()) {
            if (!dependency.isOptional() || plugins.containsKey(dependency.getPluginId())) {
                startPlugin(dependency.getPluginId());
            }
        }
        try {
            PluginState state = super.startPlugin(pluginId);
            if (previousState != state) {
                everStartedPluginIds.add(pluginId);
                PmSpringPlugin plugin = (PmSpringPlugin) wrapper.getPlugin();
                plugin.getApplicationContext()
                    .publishEvent(new PmPluginStartedEvent(pluginId, (PmPluginDescriptor) wrapper.getDescriptor()));
            }
            return state;
        } catch (Exception exception) {
            log.error("Plugin start failed：'{}',error message：{}", pluginId, exception.getMessage());
            PmPluginStartingError error = new PmPluginStartingError(pluginId, exception);
            startingErrors.put(pluginId, error);
            mainApplicationContext.publishEvent(new PmPluginStartFailedEvent(mainApplicationContext,
                    (PmPluginDescriptor) wrapper.getDescriptor(), error));
            return wrapper.getPluginState();
        }
    }

    @Override
    public void stopPlugins() {
        startingErrors.clear();
        Collections.reverse(startedPlugins);
        Iterator<PluginWrapper> iterator = startedPlugins.iterator();
        while (iterator.hasNext()) {
            PluginWrapper wrapper = iterator.next();
            PluginState previousState = wrapper.getPluginState();
            if (previousState == PluginState.STARTED) {
                try {
                    log.info("Stop plugin '{}'", getPluginLabel(wrapper.getDescriptor()));
                    wrapper.getPlugin().stop();
                    wrapper.setPluginState(PluginState.STOPPED);
                    iterator.remove();
                    firePluginStateEvent(new PluginStateEvent(this, wrapper, previousState));
                } catch (PluginRuntimeException exception) {
                    log.error(exception.getMessage(), exception);
                    startingErrors.put(wrapper.getPluginId(),
                            new PmPluginStartingError(wrapper.getPluginId(), exception));
                }
            }
        }
        if (!startingErrors.isEmpty()) {
            log.error("[PF4J] Plugin stopped failures ({}):", startingErrors.size());
            logPluginErrors();
        }
    }

    @Override
    public PluginState stopPlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper == null) {
            log.info("Plugin already unloaded or not found: '{}'", pluginId);
            return PluginState.UNLOADED;
        }
        PluginState previousState = wrapper.getPluginState();
        if (previousState.isStopped()) {
            log.info("Already stopped plugin '{}'", pluginId);
            return PluginState.STOPPED;
        }

        List<String> dependents = dependencyResolver.getDependents(pluginId);
        while (!dependents.isEmpty()) {
            String dependent = dependents.remove(0);
            ReentrantLock lock = getPluginLock(dependent);
            lock.lock();
            try {
                stopPlugin(dependent);
            } finally {
                lock.unlock();
            }
            dependents.addAll(0, dependencyResolver.getDependents(dependent));
        }

        try {
            return super.stopPlugin(pluginId);
        } catch (Exception exception) {
            log.error("Plugin stopped failed：'{}',error message：{}", pluginId, exception.getMessage());
            startingErrors.put(pluginId, new PmPluginStartingError(pluginId, exception));
            return wrapper.getPluginState();
        }
    }

    public void restartPlugins() {
        stopPlugins();
        startPlugins();
    }

    public PluginState restartPlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper == null) {
            return PluginState.UNLOADED;
        }
        if (wrapper.getPluginState() != PluginState.DISABLED) {
            stopPlugin(pluginId);
        }
        return startPlugin(pluginId);
    }

    public PluginState installPlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            Path pluginDir = pluginsRoot.resolve(pluginId);
            if (!Files.isDirectory(pluginDir)) {
                throw new IllegalStateException("Plugin directory not found: " + pluginDir);
            }
            Path jarPath;
            try (Stream<Path> paths = Files.list(pluginDir)) {
                jarPath = paths.filter(Files::isRegularFile).filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.startsWith(pluginId + "-") && fileName.endsWith(".jar");
                })
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalStateException("No JAR file found in plugin directory: " + pluginDir));
            }
            log.info("[HotReload] Installing plugin {}, JAR: {}", pluginId, jarPath);
            loadPlugin(jarPath);
            PluginState state = startPlugin(pluginId);
            PmSpringPlugin plugin = (PmSpringPlugin) getPlugin(pluginId).getPlugin();
            plugin.getApplicationContext()
                .publishEvent(new PmPluginAfterInstallEvent((PmPluginDescriptor) getPlugin(pluginId).getDescriptor()));
            log.info("[HotReload] Plugin {} installed, state: {}", pluginId, state);
            return state;
        } catch (IOException exception) {
            log.error("[HotReload] Failed to install plugin {}: {}", pluginId, exception.getMessage(), exception);
            throw new RuntimeException("Failed to install plugin: " + pluginId, exception);
        } finally {
            lock.unlock();
        }
    }

    public void reloadPlugins(boolean restartStartedOnly) {
        final List<String> startedPluginIds = getStartedPlugins().stream().map(PluginWrapper::getPluginId).toList();
        stopPlugins();
        List<String> loadedPluginIds = getPlugins().stream().map(PluginWrapper::getPluginId).toList();
        loadedPluginIds.forEach(this::unloadPlugin);
        loadPlugins();
        if (restartStartedOnly) {
            startedPluginIds.stream().filter(pluginId -> getPlugin(pluginId) != null).forEach(this::startPlugin);
        } else {
            startPlugins();
        }
    }

    public PluginState reloadPlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper == null) {
            Path pluginDir = pluginsRoot.resolve(pluginId);
            try (Stream<Path> paths = Files.list(pluginDir)) {
                Path pluginPath = paths.filter(Files::isRegularFile).filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.startsWith(pluginId + "-") && fileName.endsWith(".jar");
                })
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalStateException("No JAR file found in plugin directory: " + pluginDir));
                loadPlugin(pluginPath);
                return startPlugin(pluginId);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to reload plugin: " + pluginId, exception);
            }
        }
        PluginState previousState = wrapper.getPluginState();
        if (previousState == PluginState.RESOLVED || previousState == PluginState.STOPPED) {
            return startPlugin(pluginId);
        }
        if (previousState == PluginState.STARTED) {
            stopPlugin(pluginId);
            return startPlugin(pluginId);
        }
        log.error("Plugin reload '{}' unexpected error for state '{}'", pluginId, previousState);
        return null;
    }

    @Override
    public boolean disablePlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            PluginWrapper wrapper = getPlugin(pluginId);
            if (wrapper == null) {
                log.warn("Cannot disable unloaded plugin: {}", pluginId);
                return false;
            }
            PmSpringPlugin plugin = (PmSpringPlugin) wrapper.getPlugin();
            plugin.stop();
            wrapper.setPluginState(PluginState.DISABLED);
            mainApplicationContext
                .publishEvent(new PmPluginDisabledEvent(pluginId, (PmPluginDescriptor) wrapper.getDescriptor()));
            log.info("[Lifecycle] Plugin {} disabled", pluginId);
            return true;
        } catch (Exception exception) {
            log.error("[Lifecycle] Failed to disable plugin {}: {}", pluginId, exception.getMessage(), exception);
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    public boolean doUnloadPlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper == null) {
            log.info("Plugin already unloaded or not found: '{}'", pluginId);
            return false;
        }
        PmSpringPlugin plugin = (PmSpringPlugin) wrapper.getPlugin();
        log.info("[HotReload] Publishing BeforeUnloadEvent for plugin {}", pluginId);
        try {
            plugin.getApplicationContext()
                .publishEvent(new PmPluginBeforeUnloadEvent((PmPluginDescriptor) wrapper.getDescriptor()));
        } catch (PluginHotReloadVetoException exception) {
            log.warn("[HotReload] Unload vetoed for plugin {}: {}", pluginId, exception.getMessage());
            return false;
        }
        log.info("[HotReload] Plugin {} unloaded, ClassLoader closed, removed from registry", pluginId);
        return super.unloadPlugin(pluginId);
    }

    boolean wasEverStarted(String pluginId) {
        return everStartedPluginIds.contains(pluginId);
    }

    ReentrantLock getPluginLock(String pluginId) {
        return pluginLocks.computeIfAbsent(pluginId, key -> new ReentrantLock());
    }

    void removePluginLock(String pluginId) {
        pluginLocks.remove(pluginId);
    }

    private int getOrder(PluginWrapper wrapper) {
        if (wrapper.getDescriptor() instanceof PmPluginDescriptor descriptor) {
            return descriptor.getOrder();
        }
        return 100000;
    }

    private void logPluginErrors() {
        int index = 1;
        for (Map.Entry<String, PmPluginStartingError> entry : startingErrors.entrySet()) {
            PmPluginStartingError error = entry.getValue();
            log.error("""
                    [PF4J] Failure #{}:
                      Plugin ID : {}
                      Error     : {}
                      Detail    : {}""", index++, entry.getKey(), error.getErrorMessage(),
                    error.getErrorDetail() != null ? error.getErrorDetail() : "N/A");
        }
    }
}
