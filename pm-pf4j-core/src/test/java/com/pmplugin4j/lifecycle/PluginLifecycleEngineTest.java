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
    void registersForwardAndCleansInReverseOrder() {
        List<String> calls = new ArrayList<>();
        PluginLifecycleEngine engine =
                new PluginLifecycleEngine(List.of(registrar("second", 20, calls), registrar("first", 10, calls)));
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        engine.execute(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, context);
        engine.execute(PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE, context);

        assertEquals(List.of("start:first", "start:second", "stop:second", "stop:first"), calls);
        context.close();
    }

    @Test
    void rollsBackCompletedRegistrarsWhenStartupFails() {
        List<String> calls = new ArrayList<>();
        PluginResourceRegistrar failing =
                new PluginResourceRegistrar() {
                    @Override
                    public Set<PluginLifecyclePhase> phases() {
                        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
                    }

                    @Override
                    public int order() {
                        return 20;
                    }

                    @Override
                    public void beforeContextRefresh(AnnotationConfigApplicationContext context) {
                        calls.add("start:failing");
                        throw new IllegalStateException("boom");
                    }
                };
        PluginLifecycleEngine engine =
                new PluginLifecycleEngine(List.of(registrar("first", 10, calls), failing));
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        assertThrows(
                PluginLifecycleException.class,
                () -> engine.execute(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, context));
        assertEquals(List.of("start:first", "start:failing", "stop:first"), calls);
        context.close();
    }

    private static PluginResourceRegistrar registrar(String name, int order, List<String> calls) {
        return new PluginResourceRegistrar() {
            @Override
            public Set<PluginLifecyclePhase> phases() {
                return Set.of(
                        PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                        PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void beforeContextRefresh(AnnotationConfigApplicationContext context) {
                calls.add("start:" + name);
            }

            @Override
            public void beforeContextClose(AnnotationConfigApplicationContext context) {
                calls.add("stop:" + name);
            }
        };
    }
}
