package com.pmplugin4j.jpa;

import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

@AutoConfiguration
@ConditionalOnClass(name = "org.hibernate.jpa.HibernatePersistenceProvider")
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(PluginJpaProperties.class)
public class PluginJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JpaVendorAdapter pluginJpaVendorAdapter() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(false);
        return adapter;
    }

    @Bean
    @ConditionalOnMissingBean
    PluginJpaPersistenceManager pluginJpaPersistenceManager(DataSource dataSource, JpaVendorAdapter vendorAdapter,
            PluginJpaProperties properties) {
        return new PluginJpaPersistenceManager(dataSource, vendorAdapter, properties);
    }

    @Bean
    PluginResourceRegistrar pluginJpaRegistrar() {
        return new PluginJpaRegistrar();
    }
}
