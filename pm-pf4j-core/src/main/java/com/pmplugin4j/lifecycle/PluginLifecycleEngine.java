package com.pmplugin4j.lifecycle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Dispatches lifecycle phases to registered {@link PluginResourceRegistrar}s.
 *
 * <p>
 * Failures from framework registrars propagate to the plugin lifecycle. Failures from external registrars are isolated
 * and logged so that an optional host extension cannot prevent the plugin from starting or stopping.
 */
public final class PluginLifecycleEngine {

    private static final Logger log = LoggerFactory.getLogger(PluginLifecycleEngine.class);

    private final List<PluginResourceRegistrar> registrars;
    private final Set<PluginResourceRegistrar> externalRegistrars;

    private PluginLifecycleEngine(ApplicationContext hostContext, List<PluginResourceRegistrar> builtInRegistrars,
            List<PluginResourceRegistrar> programmaticRegistrars) {
        // External registrars come from host Spring beans and the programmatic API. Framework
        // registrars discovered as Spring beans are kept in the framework failure domain.
        List<PluginResourceRegistrar> springRegistrars = new ArrayList<>(
                hostContext.getBeansOfType(PluginResourceRegistrar.class, false, false).values());
        List<PluginResourceRegistrar> external = new ArrayList<>();
        springRegistrars.stream()
            .filter(registrar -> !(registrar instanceof BuiltInPluginResourceRegistrar))
            .forEach(external::add);
        external.addAll(programmaticRegistrars);

        // Merge framework and external registrars while preserving source order and removing
        // duplicate instances.
        LinkedHashSet<PluginResourceRegistrar> all = new LinkedHashSet<>(builtInRegistrars);
        all.addAll(springRegistrars);
        all.addAll(programmaticRegistrars);
        this.registrars = List.copyOf(all);
        this.externalRegistrars = Set.copyOf(external);
    }

    /**
     * Assembles the lifecycle engine from framework registrars, host Spring beans, and registrars added through the
     * programmatic API.
     */
    public static PluginLifecycleEngine create(ApplicationContext hostContext,
            List<PluginResourceRegistrar> builtInRegistrars, List<PluginResourceRegistrar> programmaticRegistrars) {
        return new PluginLifecycleEngine(hostContext, builtInRegistrars, programmaticRegistrars);
    }

    /** Executes all registrars participating in one lifecycle phase. */
    public void executePhase(PluginLifecyclePhase phase, AnnotationConfigApplicationContext pluginContext) {
        if (phase == null || pluginContext == null) {
            log.error("Lifecycle phase invoked with null: phase={}, context={}", phase, pluginContext);
            return;
        }
        long start = System.currentTimeMillis();
        Comparator<PluginResourceRegistrar> order = Comparator.comparingInt(PluginResourceRegistrar::order)
            .thenComparing(registrar -> registrar.getClass().getName());
        if (phase == PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE) {
            order = order.reversed();
        }

        // All registrars share one ordered pipeline. Closing reverses the order used during startup.
        List<String> completed = new ArrayList<>();
        for (PluginResourceRegistrar registrar : registrars.stream()
            .filter(candidate -> candidate.phases().contains(phase))
            .sorted(order)
            .toList()) {
            if (externalRegistrars.contains(registrar)) {
                if (dispatchExternal(registrar, phase, pluginContext)) {
                    completed.add(registrar.getClass().getSimpleName());
                }
            } else {
                dispatch(registrar, phase, pluginContext);
                completed.add(registrar.getClass().getSimpleName());
            }
        }
        log.info("Lifecycle phase {} for plugin '{}': {} registrars executed ({} ms) - {}", phase,
                pluginContext.getId(), completed.size(), System.currentTimeMillis() - start,
                String.join(", ", completed));
    }

    private static boolean dispatchExternal(PluginResourceRegistrar registrar, PluginLifecyclePhase phase,
            AnnotationConfigApplicationContext pluginContext) {
        try {
            dispatch(registrar, phase, pluginContext);
            return true;
        } catch (Exception exception) {
            log.error("Registrar {} failed at phase {} for plugin '{}': {}", registrar.getClass().getName(), phase,
                    pluginContext.getId(), exception.getMessage(), exception);
            return false;
        }
    }

    private static void dispatch(PluginResourceRegistrar registrar, PluginLifecyclePhase phase,
            AnnotationConfigApplicationContext pluginContext) {
        switch (phase) {
            case BEFORE_CONTEXT_REFRESH -> registrar.onBeforeContextRefresh(pluginContext);
            case AFTER_CONTEXT_REFRESH -> registrar.onAfterContextRefresh(pluginContext);
            case BEFORE_CONTEXT_CLOSE -> registrar.onBeforeContextClose(pluginContext);
            default -> throw new IllegalArgumentException("Unsupported lifecycle phase: " + phase);
        }
    }
}
