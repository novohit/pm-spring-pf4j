package com.pmplugin4j.lifecycle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Executes plugin resource registrars in forward order and cleanup in reverse order. */
public final class PluginLifecycleEngine {

    private static final Comparator<PluginResourceRegistrar> ORDER =
            Comparator.comparingInt(PluginResourceRegistrar::order)
                    .thenComparing(registrar -> registrar.getClass().getName());

    private final List<PluginResourceRegistrar> registrars;
    private final Map<AnnotationConfigApplicationContext, List<PluginResourceRegistrar>> executed =
            new IdentityHashMap<>();

    public PluginLifecycleEngine(List<PluginResourceRegistrar> registrars) {
        this.registrars = registrars.stream().distinct().sorted(ORDER).toList();
    }

    public synchronized void execute(
            PluginLifecyclePhase phase, AnnotationConfigApplicationContext pluginContext) {
        if (phase == PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE) {
            cleanup(pluginContext);
            return;
        }

        List<PluginResourceRegistrar> completed =
                executed.computeIfAbsent(pluginContext, ignored -> new ArrayList<>());
        for (PluginResourceRegistrar registrar : registrars) {
            if (!registrar.phases().contains(phase)) {
                continue;
            }
            try {
                invoke(registrar, phase, pluginContext);
                if (!completed.contains(registrar)) {
                    completed.add(registrar);
                }
            } catch (RuntimeException exception) {
                cleanup(pluginContext);
                throw new PluginLifecycleException(
                        "Plugin lifecycle phase " + phase + " failed in "
                                + registrar.getClass().getName(), exception);
            }
        }
    }

    private void cleanup(AnnotationConfigApplicationContext pluginContext) {
        List<PluginResourceRegistrar> completed = executed.remove(pluginContext);
        if (completed == null) {
            completed = registrars;
        }
        RuntimeException firstFailure = null;
        for (int index = completed.size() - 1; index >= 0; index--) {
            PluginResourceRegistrar registrar = completed.get(index);
            if (!registrar.phases().contains(PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE)) {
                continue;
            }
            try {
                registrar.beforeContextClose(pluginContext);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        if (firstFailure != null) {
            throw new PluginLifecycleException("Plugin resource cleanup failed", firstFailure);
        }
    }

    private static void invoke(
            PluginResourceRegistrar registrar,
            PluginLifecyclePhase phase,
            AnnotationConfigApplicationContext pluginContext) {
        switch (phase) {
            case BEFORE_CONTEXT_REFRESH -> registrar.beforeContextRefresh(pluginContext);
            case AFTER_CONTEXT_REFRESH -> registrar.afterContextRefresh(pluginContext);
            case BEFORE_CONTEXT_CLOSE -> registrar.beforeContextClose(pluginContext);
        }
    }
}
