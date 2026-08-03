
package com.pmplugin4j.webflux;

import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;

public class PmPluginWebFluxSecureServerWebExchange extends ServerWebExchangeDecorator {

    public PmPluginWebFluxSecureServerWebExchange(ServerWebExchange delegate) {
        super(delegate);
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return null;
    }
}
