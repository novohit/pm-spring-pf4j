package com.pmplugin4j.manager;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PmPluginBootstrapTest {
    @Test
    void loadsBeforeStartingTenantSelectedPlugins() {
        PmPluginManager manager = mock(PmPluginManager.class);
        TenantPluginSelector selector = mock(TenantPluginSelector.class);
        when(selector.selectEnabledPlugins()).thenReturn(List.of("one", "two"));
        new PmPluginBootstrap(manager, selector).startPlugins();
        InOrder order = inOrder(selector, manager);
        order.verify(selector).selectEnabledPlugins();
        order.verify(manager).loadPlugins();
        order.verify(manager).startPlugins(List.of("one", "two"));
    }
}
