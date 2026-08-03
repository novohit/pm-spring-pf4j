package com.pmplugin4j.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class AuthChainExecutorTest {

    @Test
    void returnsNotClaimedWhenNoProviderSupportsRequest() {
        IPluginAuthenticationProvider provider = provider(false, null);
        AuthChainExecutor.AuthChainResult result = AuthChainExecutor.execute(List.of(provider),
                mock(HttpServletRequest.class), new AtLeastOneSuccessfulStrategy(), "sample");
        assertInstanceOf(AuthChainExecutor.AuthChainResult.NotClaimed.class, result);
    }

    @Test
    void returnsFirstSuccessfulAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("plugin-user", null, List.of());
        AuthChainExecutor.AuthChainResult result = AuthChainExecutor.execute(
                List.of(provider(true, null), provider(true, authentication)), mock(HttpServletRequest.class),
                new AtLeastOneSuccessfulStrategy(), "sample");
        AuthChainExecutor.AuthChainResult.Success success = assertInstanceOf(
                AuthChainExecutor.AuthChainResult.Success.class, result);
        assertEquals("plugin-user", success.authentication().getName());
    }

    @Test
    void stopsImmediatelyForAuthenticationChallenge() {
        IPluginAuthenticationProvider provider = new IPluginAuthenticationProvider() {
            @Override
            public Authentication authenticate(HttpServletRequest request) {
                throw new PluginAuthChallenge(401, Map.of("WWW-Authenticate", "Bearer"));
            }
        };
        AuthChainExecutor.AuthChainResult result = AuthChainExecutor.execute(List.of(provider),
                mock(HttpServletRequest.class), new AtLeastOneSuccessfulStrategy(), "sample");
        AuthChainExecutor.AuthChainResult.Challenge challenge = assertInstanceOf(
                AuthChainExecutor.AuthChainResult.Challenge.class, result);
        assertEquals(401, challenge.exception().getStatusCode());
    }

    private static IPluginAuthenticationProvider provider(boolean supported, Authentication authentication) {
        return new IPluginAuthenticationProvider() {
            @Override
            public boolean supports(HttpServletRequest request) {
                return supported;
            }

            @Override
            public Authentication authenticate(HttpServletRequest request) {
                return authentication;
            }
        };
    }
}
