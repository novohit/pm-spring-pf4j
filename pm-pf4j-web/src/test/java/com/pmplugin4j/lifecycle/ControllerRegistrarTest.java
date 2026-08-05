package com.pmplugin4j.lifecycle;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pmplugin4j.webflux.PmPluginWebFluxRequestMappingHandlerMapping;
import com.pmplugin4j.webflux.PmPluginWebFluxRouterFunctionRegistry;
import com.pmplugin4j.webmvc.PmPluginRequestMappingHandlerMapping;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.util.LinkedMultiValueMap;

class ControllerRegistrarTest {

    @Test
    void choosesWebFluxBeforeMvcAndUsesTheSameBranchForCleanup() {
        PmPluginWebFluxRequestMappingHandlerMapping webFlux = mock(PmPluginWebFluxRequestMappingHandlerMapping.class);
        PmPluginWebFluxRouterFunctionRegistry routerFunctions = mock(PmPluginWebFluxRouterFunctionRegistry.class);
        PmPluginRequestMappingHandlerMapping mvc = mock(PmPluginRequestMappingHandlerMapping.class);
        when(webFlux.getPluginRequestMappingInfo()).thenReturn(new LinkedMultiValueMap<>());
        AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
        host.getBeanFactory().registerSingleton("webFlux", webFlux);
        host.getBeanFactory().registerSingleton("routerFunctions", routerFunctions);
        host.getBeanFactory().registerSingleton("mvc", mvc);
        host.refresh();
        AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext();
        plugin.setId("sample");
        plugin.setParent(host);
        plugin.refresh();

        ControllerRegistrar registrar = new ControllerRegistrar();
        registrar.onAfterContextRefresh(plugin);
        verify(webFlux).registerControllers("sample", plugin);
        verify(mvc, never()).registerControllers("sample", plugin);

        registrar.onBeforeContextClose(plugin);
        verify(webFlux).unregisterHandlerMethods("sample");
        verify(mvc, never()).unregisterController("sample");
        plugin.close();
        host.close();
    }
}
