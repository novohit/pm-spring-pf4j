/*
 * Copyright (c) 2025 grejeff.
 */

package com.pmplugin4j.webflux;

import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;
import org.springframework.web.server.ServerWebExchange;

public class PmPluginWebFluxSecureServerRequest extends ServerRequestWrapper {

    public PmPluginWebFluxSecureServerRequest(ServerRequest delegate) {
        super(delegate);
    }

    @Override
    @NonNull
    public ServerWebExchange exchange() {
        return new PmPluginWebFluxSecureServerWebExchange(super.exchange());
    }
}
