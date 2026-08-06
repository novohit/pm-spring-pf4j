package com.pmplugin4j.manager;

import com.pmplugin4j.api.PmSpringPlugin;
import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.descriptor.PmPluginDescriptor;
import com.pmplugin4j.event.PmPluginDisabledEvent;
import com.pmplugin4j.event.PmPluginStartFailedEvent;
import com.pmplugin4j.event.PmPluginStartedEvent;
import com.pmplugin4j.event.PmPluginStartingError;
import com.pmplugin4j.factory.PmPluginFactory;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
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
import org.pf4j.ExtensionFactory;
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
    private final Map<String, ReentrantLock> pluginLocks = new ConcurrentHashMap<>();
    private final Set<String> everStartedPluginIds = ConcurrentHashMap.newKeySet();
    private final List<PluginResourceRegistrar> externalRegistrars = new ArrayList<>();
    private final PluginProperties pluginProperties;
    private GenericApplicationContext mainApplicationContext;
    private volatile boolean registrarsFrozen;

    public PmPluginManager(Path pluginsRoot, PluginProperties pluginProperties) {
        super(pluginsRoot);
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
        startingErrors.clear();
        getResolvedPlugins().stream()
            .sorted(Comparator.comparingInt(this::getOrder))
            .map(PluginWrapper::getPluginId)
            .forEach(this::startPlugin);
        if (!startingErrors.isEmpty()) {
            log.error("[PF4J] Plugin startup failures ({}):", startingErrors.size());
            logPluginErrors();
        }
    }

    public void startPlugins(Collection<String> enabledPluginIds) {
        startingErrors.clear();
        getResolvedPlugins().stream()
            .filter(wrapper -> enabledPluginIds.contains(wrapper.getPluginId()))
            .sorted(Comparator.comparingInt(this::getOrder))
            .map(PluginWrapper::getPluginId)
            .forEach(this::startPlugin);
        if (!startingErrors.isEmpty()) {
            log.error("[PF4J] Plugin startup failures ({}):", startingErrors.size());
            logPluginErrors();
        }
    }

    @Override
    public PluginState startPlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper == null) {
            return PluginState.UNLOADED;
        }
        PluginState previousState = wrapper.getPluginState();
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
                    wrapper.getPlugin().stop();
                    wrapper.setPluginState(PluginState.STOPPED);
                    iterator.remove();
                    firePluginStateEvent(new PluginStateEvent(this, wrapper, previousState));
                } catch (PluginRuntimeException exception) {
                    startingErrors.put(wrapper.getPluginId(),
                            new PmPluginStartingError(wrapper.getPluginId(), exception));
                }
            }
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

    @Override
    public boolean disablePlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            PluginWrapper wrapper = getPlugin(pluginId);
            if (wrapper == null) {
                return false;
            }
            PmSpringPlugin plugin = (PmSpringPlugin) wrapper.getPlugin();
            plugin.stop();
            wrapper.setPluginState(PluginState.DISABLED);
            mainApplicationContext
                .publishEvent(new PmPluginDisabledEvent(pluginId, (PmPluginDescriptor) wrapper.getDescriptor()));
            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean wasEverStarted(String pluginId) {
        return everStartedPluginIds.contains(pluginId);
    }

    private ReentrantLock getPluginLock(String pluginId) {
        return pluginLocks.computeIfAbsent(pluginId, key -> new ReentrantLock());
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
            log.error("[PF4J] Failure #{}: plugin={}, error={}, detail={}", index++, entry.getKey(),
                    error.getErrorMessage(), error.getErrorDetail());
        }
    }
}
