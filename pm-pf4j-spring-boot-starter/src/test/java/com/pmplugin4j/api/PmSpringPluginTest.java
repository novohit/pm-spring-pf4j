package com.pmplugin4j.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.descriptor.PmPluginDescriptor;
import com.pmplugin4j.event.PmPluginRestartedEvent;
import com.pmplugin4j.event.PmPluginStartingEvent;
import com.pmplugin4j.event.PmPluginStoppedEvent;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PmSpringPluginTest {

    @Test
    void contextIsUnavailableBeforeStartAndStopIsSafe() {
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PmSpringPlugin plugin = plugin(wrapper(), new TestBusinessPlugin(), host, List.of());

            assertThrows(IllegalStateException.class, plugin::getApplicationContext);
            plugin.stop();
        }
    }

    @Test
    void rejectsPluginIdThatDoesNotMatchBusinessPluginPackage() {
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PmSpringPlugin plugin = plugin(wrapper("wrong.plugin.id"), new TestBusinessPlugin(), host, List.of());

            assertThrows(PluginRuntimeException.class, plugin::start);
        }
    }

    @Test
    void closesRefreshedContextWhenPostRefreshHookFails() {
        FailingAfterReadyPlugin businessPlugin = new FailingAfterReadyPlugin();
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PmSpringPlugin plugin = plugin(wrapper(), businessPlugin, host, List.of());

            assertThrows(PluginRuntimeException.class, plugin::start);
            assertFalse(businessPlugin.context.isActive());
            assertThrows(IllegalStateException.class, plugin::getApplicationContext);
        }
    }

    @Test
    void startStopAndRestartOwnTheSpringContextLifecycle() {
        List<String> calls = new ArrayList<>();
        PluginWrapper wrapper = wrapper();
        TestBusinessPlugin businessPlugin = new TestBusinessPlugin();
        PluginResourceRegistrar registrar = registrar(calls);
        List<Class<?>> events = new ArrayList<>();

        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.addApplicationListener(event -> {
                if (event instanceof PmPluginStartingEvent || event instanceof PmPluginRestartedEvent
                        || event instanceof PmPluginStoppedEvent) {
                    events.add(event.getClass());
                }
            });
            host.refresh();
            PmSpringPlugin plugin = new com.pmplugin4j.api.PmSpringPlugin(wrapper, businessPlugin, host,
                    new PluginProperties(), List.of(), List.of(registrar));

            plugin.start();
            ApplicationContext first = plugin.getApplicationContext();
            assertTrue(((AnnotationConfigApplicationContext) first).isActive());
            plugin.stop();
            assertFalse(((AnnotationConfigApplicationContext) first).isActive());

            plugin.start();
            ApplicationContext second = plugin.getApplicationContext();
            assertNotSame(first, second);
            plugin.stop();
        }

        assertTrue(businessPlugin.beforeCalls == 2 && businessPlugin.afterCalls == 2);
        assertTrue(calls.equals(List.of("before", "after", "close", "before", "after", "close")));
        assertEquals(List.of(PmPluginStartingEvent.class, PmPluginStoppedEvent.class, PmPluginStartingEvent.class,
                PmPluginRestartedEvent.class, PmPluginStoppedEvent.class), events);
    }

    private static PluginWrapper wrapper() {
        return wrapper("com.pmplugin4j.api");
    }

    private static PluginWrapper wrapper(String pluginId) {
        PluginWrapper wrapper = mock(PluginWrapper.class);
        PmPluginDescriptor descriptor = mock(PmPluginDescriptor.class);
        when(wrapper.getPluginId()).thenReturn(pluginId);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(wrapper.getPluginClassLoader()).thenReturn(PmSpringPluginTest.class.getClassLoader());
        when(descriptor.getPluginClass()).thenReturn(TestBusinessPlugin.class.getName());
        when(descriptor.getPluginId()).thenReturn(pluginId);
        return wrapper;
    }

    private static PmSpringPlugin plugin(PluginWrapper wrapper, PmPlugin businessPlugin, ApplicationContext host,
            List<PluginResourceRegistrar> registrars) {
        return new PmSpringPlugin(wrapper, businessPlugin, host, new PluginProperties(), List.of(), registrars);
    }

    private static PluginResourceRegistrar registrar(List<String> calls) {
        return new PluginResourceRegistrar() {
            @Override
            public Set<PluginLifecyclePhase> phases() {
                return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                        PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
            }

            @Override
            public void onBeforeContextRefresh(AnnotationConfigApplicationContext context) {
                calls.add("before");
            }

            @Override
            public void onAfterContextRefresh(AnnotationConfigApplicationContext context) {
                calls.add("after");
            }

            @Override
            public void onBeforeContextClose(AnnotationConfigApplicationContext context) {
                calls.add("close");
            }
        };
    }

    static final class TestBusinessPlugin extends PmPlugin {
        int beforeCalls;
        int afterCalls;

        @Override
        protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
                AnnotationConfigApplicationContext context) {
            beforeCalls++;
            return context;
        }

        @Override
        protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {
            afterCalls++;
        }
    }

    static final class FailingAfterReadyPlugin extends PmPlugin {
        AnnotationConfigApplicationContext context;

        @Override
        protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
                AnnotationConfigApplicationContext context) {
            this.context = context;
            return context;
        }

        @Override
        protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {
            throw new IllegalStateException("after-ready failure");
        }
    }
}
