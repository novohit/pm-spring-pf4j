package com.pmplugin4j.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmplugin4j.core.AllowAnonymous;
import com.pmplugin4j.core.DefaultPluginAnonymousPathRegistry;
import com.pmplugin4j.core.PluginAuthenticated;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

class PmPluginRequestMappingHandlerMappingTest {

    @Test
    void preservesSpringMvcMappingSemanticsAndSecurityMetadata() {
        AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
        host.refresh();
        DefaultPluginAnonymousPathRegistry anonymousPaths = new DefaultPluginAnonymousPathRegistry();
        PmPluginRequestMappingHandlerMapping mapping = new PmPluginRequestMappingHandlerMapping();
        mapping.setApplicationContext(host);
        mapping.setAnonymousPathRegistry(anonymousPaths);
        mapping.afterPropertiesSet();

        AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext();
        plugin.setId("sample");
        plugin.registerBean(SampleController.class);
        plugin.refresh();
        mapping.registerControllers("sample", plugin);

        assertEquals(2, mapping.getPluginMappingInfo().get("sample").size());
        Map<String, Set<String>> paths = mapping.getPluginPaths("sample");
        assertTrue(paths.get("PATCH").contains("/first/items/{id}"));
        assertTrue(paths.get("PATCH").contains("/second/items/{id}"));
        assertTrue(paths.get("POST").contains("/first/general"));
        assertTrue(mapping.getPluginAuthenticatedPaths("sample").get("PATCH").contains("/first/items/{id}"));
        assertFalse(mapping.getPluginAuthenticatedPaths("sample")
            .getOrDefault("POST", Set.of())
            .contains("/first/general"));
        assertTrue(anonymousPaths.isAnonymous("/first/general", "POST"));

        mapping.unregisterController("sample");
        assertTrue(mapping.getPluginMappingInfo().getOrDefault("sample", java.util.List.of()).isEmpty());
        assertFalse(anonymousPaths.isAnonymous("/first/general", "POST"));
        plugin.close();
        host.close();
    }

    @RestController
    @PluginAuthenticated
    @RequestMapping({"/first", "/second"})
    static class SampleController {

        @PatchMapping(path = "/items/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
        String patch() {
            return "patched";
        }

        @AllowAnonymous(reason = "callback")
        @RequestMapping(path = "/general", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
        String general() {
            return "accepted";
        }
    }
}
