package com.pmplugin4j.webflux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.ServerResponse;

class PmRouterFunctionsTest {

    @Test
    void retainsAnonymousAndPluginAuthenticatedDeclarations() {
        PmRouterFunctions.AnnotatedRouterFunction routes = (PmRouterFunctions.AnnotatedRouterFunction) PmRouterFunctions
            .route()
            .GET("/public", request -> ServerResponse.ok().build(), "health endpoint")
            .POST("/secure", request -> ServerResponse.ok().build())
            .pluginAuthenticated()
            .build();

        assertEquals("health endpoint", routes.getDeclarations().getFirst().reason());
        assertEquals("/secure", routes.getPluginAuthenticatedDeclarations().getFirst().pathPattern());
    }

    @Test
    void registryTracksAndRemovesFunctionalRouteMetadataByPlugin() {
        PmRouterFunctions.AnnotatedRouterFunction routes = (PmRouterFunctions.AnnotatedRouterFunction) PmRouterFunctions
            .route()
            .GET("/public", request -> ServerResponse.ok().build(), "health endpoint")
            .POST("/secure", request -> ServerResponse.ok().build())
            .pluginAuthenticated()
            .build();
        PmPluginWebFluxDefaultRouterFunctionRegistry registry = new PmPluginWebFluxDefaultRouterFunctionRegistry();

        registry.register("example", List.of(routes));

        assertTrue(registry.getRouterFunctionPaths("example").get("GET").contains("/public"));
        assertTrue(registry.getRouterFunctionAuthenticatedPaths("example").get("POST").contains("/secure"));

        registry.unregister("example", List.of(routes));
        assertTrue(registry.getRouterFunctionPaths("example").isEmpty());
        assertTrue(registry.getRouterFunctionAuthenticatedPaths("example").isEmpty());
    }
}
