package com.pmplugin4j.mybatis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Activates the MyBatis lifecycle registrar when this optional integration is present. */
@Configuration
public class PmPluginMybatisAutoConfiguration {

    @Bean
    public PluginMybatisRegistrar pluginMybatisRegistrar() {
        return new PluginMybatisRegistrar();
    }
}
