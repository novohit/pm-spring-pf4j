package com.pmplugin4j.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplugin4j.security.reactive.PluginCompositeWebFilter;
import com.pmplugin4j.security.reactive.PluginDelegatingAuthWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

class PmSecurityWebFluxAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
        .withConfiguration(
                AutoConfigurations.of(PmSecurityAutoConfiguration.class, PmSecurityWebFluxAutoConfiguration.class));

    @Test
    void configuresAllReactiveExtensionSlots() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReactiveFilterRegistrar.class);
            assertThat(context).hasSingleBean(PluginDelegatingAuthWebFilter.class);
            assertThat(context).getBeans(PluginCompositeWebFilter.class)
                .containsOnlyKeys("pluginFirstWebFilter", "pluginSessionRestoreWebFilter", "pluginFormLoginWebFilter",
                        "pluginAnonymousWebFilter", "pluginPreAuthorizeWebFilter", "pluginLastWebFilter");
        });
    }
}
