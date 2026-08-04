package com.pmplugin4j.sample.host;

import com.pmplugin4j.core.PluginAnonymousPathRegistry;
import com.pmplugin4j.security.servlet.PluginSecurityConfigurer;
import com.pmplugin4j.utils.PluginHttpUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Demonstrates host-owned authorization around plugin-provided authentication. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class MvcSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, PluginSecurityConfigurer pluginSecurity,
            PluginAnonymousPathRegistry anonymousPaths) throws Exception {
        http.with(pluginSecurity, Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(request -> anonymousPaths
                    .isAnonymous(PluginHttpUtils.getPathWithinApplication(request), request.getMethod()))
                .permitAll()
                .requestMatchers("/sample-security/**")
                .authenticated()
                .anyRequest()
                .permitAll())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable());
        return http.build();
    }
}
