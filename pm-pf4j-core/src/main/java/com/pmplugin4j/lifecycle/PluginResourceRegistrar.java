package com.pmplugin4j.lifecycle;

import java.util.Set;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Plugin resource registrar — registers and unregisters plugin resources at specific lifecycle
 * phases.
 *
 * <p>All methods are default methods; implementations only need to override the phases they use.
 * External registrars are Spring singleton beans, so they must be stateless. All per-plugin state
 * must be obtained from the callback parameter.
 *
 * <p>The host {@code ApplicationContext} is available through {@code pluginContext.getParent()}.
 * The plugin ID is available through {@code pluginContext.getId()}.
 */
public interface PluginResourceRegistrar {

    /** Lifecycle phases in which this registrar participates. The default is an empty no-op set. */
    default Set<PluginLifecyclePhase> phases() {
        return Set.of();
    }

    /**
     * Execution order within a phase. Smaller values execute earlier.
     *
     * <p>Framework registrars occupy values below {@code 100}. External registrars default to
     * {@code 100}, after framework registrars. An external registrar may return a lower value when
     * it must run between framework integrations.
     */
    default int order() {
        return 100;
    }

    /**
     * Called before the plugin Spring context is refreshed. The context is not active yet; register
     * bean definitions, singleton resources, and property sources here.
     *
     * @param pluginContext plugin application context that has not been refreshed
     */
    default void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginContext) {}

    /**
     * Called after the plugin Spring context has been refreshed. All plugin beans are initialized;
     * scan them and register resources with host managers here.
     *
     * @param pluginContext fully refreshed plugin application context
     */
    default void onAfterContextRefresh(AnnotationConfigApplicationContext pluginContext) {}

    /**
     * Called before the plugin Spring context is closed. Unregister resources from host managers,
     * close plugin-owned connections, and flush buffers here. The context is still active and its
     * beans remain available during this callback.
     *
     * @param pluginContext active plugin application context that is about to close
     */
    default void onBeforeContextClose(AnnotationConfigApplicationContext pluginContext) {}
}
