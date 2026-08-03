package com.pmplugin4j.core;

/** Describes an anonymous functional route declared by a plugin. */
public record AnonymousRouteDeclaration(String pathPattern, String httpMethod, String reason) {
}
