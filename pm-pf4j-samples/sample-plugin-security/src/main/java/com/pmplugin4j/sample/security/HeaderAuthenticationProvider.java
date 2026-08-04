package com.pmplugin4j.sample.security;

import com.pmplugin4j.security.IPluginAuthenticationProvider;
import com.pmplugin4j.security.PluginBadCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class HeaderAuthenticationProvider implements IPluginAuthenticationProvider {

    @Override
    public Authentication authenticate(HttpServletRequest request) {
        String token = request.getHeader("X-Plugin-Token");
        if (!"sample-token".equals(token)) {
            throw new PluginBadCredentialsException("Missing or invalid X-Plugin-Token");
        }
        return UsernamePasswordAuthenticationToken.authenticated("sample-plugin-user", token, List.of());
    }
}
