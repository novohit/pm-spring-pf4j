package com.pmplugin4j.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Activates the MyBatis lifecycle registrar when this optional integration is present. */
@AutoConfiguration
public class PmPluginMybatisAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor pluginMybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public PluginMybatisSqlSessionManager pluginMybatisSqlSessionManager(DataSource dataSource,
            MybatisPlusInterceptor mybatisPlusInterceptor, ObjectProvider<MetaObjectHandler> metaObjectHandlers) {
        // Optional for hosts without automatic filling; use @Primary when multiple handlers exist.
        return new PluginMybatisSqlSessionManager(dataSource, mybatisPlusInterceptor,
                metaObjectHandlers.getIfAvailable());
    }

    @Bean
    public PluginMybatisRegistrar pluginMybatisRegistrar() {
        return new PluginMybatisRegistrar();
    }
}
