package com.pmplugin4j.manager;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pmplugin4j.hotreload.PmPluginHotReloadManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

class PmPluginBootstrapTest {

    @Test
    void loadsBeforeStartingTenantSelectedPlugins() {
        PmPluginManager manager = mock(PmPluginManager.class);
        TenantPluginSelector selector = mock(TenantPluginSelector.class);
        PmPluginHotReloadManager hotReloadManager = mock(PmPluginHotReloadManager.class);
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        when(manager.isAutoStartPlugin()).thenReturn(true);
        when(selector.selectEnabledPlugins()).thenReturn(List.of("one", "two"));

        new PmPluginBootstrap(applicationContext, manager, selector, hotReloadManager)
            .onApplicationEvent(new ContextRefreshedEvent(applicationContext));

        InOrder order = inOrder(selector, manager, hotReloadManager);
        order.verify(selector).selectEnabledPlugins();
        order.verify(manager).loadPlugins();
        order.verify(manager).startPlugins(List.of("one", "two"));
        order.verify(manager).setMainApplicationStarted(true);
        order.verify(hotReloadManager).startWatching();
    }
}
