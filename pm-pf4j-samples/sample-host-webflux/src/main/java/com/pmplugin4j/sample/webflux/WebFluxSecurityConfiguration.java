package com.pmplugin4j.sample.webflux;

import com.pmplugin4j.core.PluginAnonymousPathRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/** Demonstrates host-owned reactive authorization around plugin-provided authentication. */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class WebFluxSecurityConfiguration {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, PluginAnonymousPathRegistry anonymousPaths) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(
                    authorize -> authorize.pathMatchers("/sample-security/**").access((authentication, context) -> {
                        String path = context.getExchange().getRequest().getPath().pathWithinApplication().value();
                        String method = context.getExchange().getRequest().getMethod().name();
                        if (anonymousPaths.isAnonymous(path, method)) {
                            return Mono.just(new AuthorizationDecision(true));
                        }
                        return authentication.map(value -> new AuthorizationDecision(value.isAuthenticated()));
                    }).anyExchange().permitAll())
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable);
        return http.build();
    }
}
