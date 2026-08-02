package com.pmplugin4j.lifecycle;

import java.util.Set;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Registers and removes one category of plugin-owned resources. Implementations must be stateless. */
public interface PluginResourceRegistrar {

    default Set<PluginLifecyclePhase> phases() {
        return Set.of();
    }

    default int order() {
        return 100;
    }

    default void beforeContextRefresh(AnnotationConfigApplicationContext pluginContext) {}

    default void afterContextRefresh(AnnotationConfigApplicationContext pluginContext) {}

    default void beforeContextClose(AnnotationConfigApplicationContext pluginContext) {}
}
