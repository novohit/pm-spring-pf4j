package com.pmplugin4j.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PluginLifecycleEngineTest {

    @Test
    void executesForwardAndClosesInReverseOrder() {
        List<String> calls = new ArrayList<>();
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PluginLifecycleEngine engine = PluginLifecycleEngine.create(host,
                    List.of(registrar("second", 20, calls), registrar("first", 10, calls)), List.of());
            AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext();

            engine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, plugin);
            engine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE, plugin);

            assertEquals(List.of("start:first", "start:second", "stop:second", "stop:first"), calls);
            plugin.close();
        }
    }

    @Test
    void isolatesExternalRegistrarFailure() {
        List<String> calls = new ArrayList<>();
        PluginResourceRegistrar failing = new PluginResourceRegistrar() {
            @Override
            public Set<PluginLifecyclePhase> phases() {
                return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
            }

            @Override
            public void onBeforeContextRefresh(AnnotationConfigApplicationContext context) {
                calls.add("external");
                throw new IllegalStateException("boom");
            }
        };
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PluginLifecycleEngine engine = PluginLifecycleEngine.create(host, List.of(), List.of(failing));
            engine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, new AnnotationConfigApplicationContext());
        }
        assertEquals(List.of("external"), calls);
    }

    @Test
    void propagatesBuiltInRegistrarFailure() {
        PluginResourceRegistrar failing = new PluginResourceRegistrar() {
            @Override
            public Set<PluginLifecyclePhase> phases() {
                return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
            }

            @Override
            public void onBeforeContextRefresh(AnnotationConfigApplicationContext context) {
                throw new IllegalStateException("boom");
            }
        };
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PluginLifecycleEngine engine = PluginLifecycleEngine.create(host, List.of(failing), List.of());
            assertThrows(IllegalStateException.class, () -> engine
                .executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, new AnnotationConfigApplicationContext()));
        }
    }

    private static PluginResourceRegistrar registrar(String name, int order, List<String> calls) {
        return new PluginResourceRegistrar() {
            @Override
            public Set<PluginLifecyclePhase> phases() {
                return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void onBeforeContextRefresh(AnnotationConfigApplicationContext context) {
                calls.add("start:" + name);
            }

            @Override
            public void onBeforeContextClose(AnnotationConfigApplicationContext context) {
                calls.add("stop:" + name);
            }
        };
    }
}
