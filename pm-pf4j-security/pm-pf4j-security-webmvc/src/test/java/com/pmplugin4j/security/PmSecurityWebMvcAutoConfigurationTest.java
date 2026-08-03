package com.pmplugin4j.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pmplugin4j.security.servlet.PluginCompositeFilter;
import com.pmplugin4j.security.servlet.PluginDelegatingAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class PmSecurityWebMvcAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(PmSecurityAutoConfiguration.class, PmSecurityWebMvcAutoConfiguration.class));

    @Test
    void configuresAllServletExtensionSlots() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ServletFilterRegistrar.class);
            assertThat(context).hasSingleBean(PluginDelegatingAuthFilter.class);
            assertThat(context).getBeans(PluginCompositeFilter.class)
                .containsOnlyKeys("pluginFirstFilter", "pluginSessionRestoreFilter", "pluginFormLoginFilter",
                        "pluginAnonymousFilter", "pluginPreAuthorizeFilter", "pluginLastFilter");
        });
    }
}
