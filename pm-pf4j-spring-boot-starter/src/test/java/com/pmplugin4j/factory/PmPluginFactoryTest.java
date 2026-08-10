package com.pmplugin4j.factory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pmplugin4j.api.PmPlugin;
import com.pmplugin4j.api.PmSpringPlugin;
import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.manager.PmPluginManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class PmPluginFactoryTest {

    @Test
    void wrapsAValidBusinessPlugin() {
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PmPluginFactory factory = new PmPluginFactory();

            assertInstanceOf(PmSpringPlugin.class, factory.create(wrapper(ValidPlugin.class, host)));
        }
    }

    @Test
    void rejectsAClassThatIsNotAPmPlugin() {
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext()) {
            host.refresh();
            PmPluginFactory factory = new PmPluginFactory();

            assertThrows(RuntimeException.class, () -> factory.create(wrapper(String.class, host)));
        }
    }

    private static PluginWrapper wrapper(Class<?> pluginClass, AnnotationConfigApplicationContext host) {
        PluginWrapper wrapper = mock(PluginWrapper.class);
        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        PmPluginManager pluginManager = mock(PmPluginManager.class);
        when(wrapper.getPluginId()).thenReturn(pluginClass.getPackageName());
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(wrapper.getPluginClassLoader()).thenReturn(PmPluginFactoryTest.class.getClassLoader());
        when(wrapper.getPluginManager()).thenReturn(pluginManager);
        when(descriptor.getPluginClass()).thenReturn(pluginClass.getName());
        when(pluginManager.getMainApplicationContext()).thenReturn(host);
        when(pluginManager.getPluginProperties()).thenReturn(new PluginProperties());
        when(pluginManager.getExternalRegistrars()).thenReturn(List.of());
        return wrapper;
    }

    public static final class ValidPlugin extends PmPlugin {
        @Override
        protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
                AnnotationConfigApplicationContext context) {
            return context;
        }

        @Override
        protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {
        }
    }
}
