package com.pmplugin4j.jpa;

import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/** Creates and tracks one JPA persistence unit per plugin. */
public final class PluginJpaManager {

    private final DataSource dataSource;
    private final JpaVendorAdapter vendorAdapter;
    private final PluginJpaProperties properties;
    private final Set<String> initializedPlugins = ConcurrentHashMap.newKeySet();

    public PluginJpaManager(DataSource dataSource, JpaVendorAdapter vendorAdapter, PluginJpaProperties properties) {
        this.dataSource = dataSource;
        this.vendorAdapter = vendorAdapter;
        this.properties = properties;
    }

    public void initialize(String pluginId, String entityPackage, AnnotationConfigApplicationContext pluginContext) {
        if (!initializedPlugins.add(pluginId)) {
            return;
        }
        try {
            final DefaultListableBeanFactory beans = pluginContext.getDefaultListableBeanFactory();
            final String emfName = pluginId + "_entityManagerFactory";
            BeanDefinitionBuilder emf = BeanDefinitionBuilder
                .genericBeanDefinition(LocalContainerEntityManagerFactoryBean.class);
            emf.addPropertyValue("dataSource", dataSource);
            emf.addPropertyValue("packagesToScan", new String[]{entityPackage});
            emf.addPropertyValue("jpaVendorAdapter", vendorAdapter);
            emf.addPropertyValue("jpaPropertyMap", properties.asJpaProperties());
            emf.addPropertyValue("persistenceUnitName", pluginId);
            emf.setPrimary(true);
            beans.registerBeanDefinition(emfName, emf.getBeanDefinition());

            BeanDefinitionBuilder transactionManager = BeanDefinitionBuilder
                .genericBeanDefinition(JpaTransactionManager.class);
            transactionManager.addPropertyReference("entityManagerFactory", emfName);
            transactionManager.setPrimary(true);
            beans.registerBeanDefinition(pluginId + "_transactionManager", transactionManager.getBeanDefinition());
        } catch (RuntimeException exception) {
            initializedPlugins.remove(pluginId);
            throw exception;
        }
    }

    public void cleanup(String pluginId, ApplicationContext pluginContext) {
        initializedPlugins.remove(pluginId);
        String emfName = pluginId + "_entityManagerFactory";
        if (!pluginContext.containsBean(emfName)) {
            return;
        }
        EntityManagerFactory entityManagerFactory = pluginContext.getBean(emfName, EntityManagerFactory.class);
        if (entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }
}
