package com.pmplugin4j.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pmplugin4j.jpa.entity.SampleEntity;
import com.pmplugin4j.jpa.repository.SampleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

class PluginJpaPersistenceManagerTest {

    @Test
    void registersIndependentPersistenceUnitTransactionManagerAndRepositories() {
        PluginJpaPersistenceManager manager = manager();
        try (AnnotationConfigApplicationContext plugin = plugin()) {
            manager.initializeJpaForPlugin(plugin.getId(), "com.pmplugin4j.jpa", plugin);

            BeanDefinition entityManagerFactory = plugin.getBeanFactory()
                .getBeanDefinition("com.pmplugin4j.jpa_entityManagerFactory");
            assertEquals(LocalContainerEntityManagerFactoryBean.class.getName(),
                    entityManagerFactory.getBeanClassName());
            assertTrue(entityManagerFactory.isPrimary());
            assertEquals("com.pmplugin4j.jpa", entityManagerFactory.getPropertyValues().get("persistenceUnitName"));

            BeanDefinition transactionManager = plugin.getBeanFactory()
                .getBeanDefinition("com.pmplugin4j.jpa_transactionManager");
            assertEquals(JpaTransactionManager.class.getName(), transactionManager.getBeanClassName());
            assertEquals(new RuntimeBeanReference("com.pmplugin4j.jpa_entityManagerFactory"),
                    transactionManager.getPropertyValues().get("entityManagerFactory"));

            BeanDefinition repository = plugin.getBeanFactory().getBeanDefinition("jpa.sampleRepository");
            assertEquals("org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean",
                    repository.getBeanClassName());
            assertEquals(new RuntimeBeanReference("com.pmplugin4j.jpa_sharedEntityManager"),
                    repository.getPropertyValues().get("entityManager"));

            assertTrue(plugin.containsBean("com.pmplugin4j.jpa_persistenceExceptionTranslator"));
            assertEquals(1, manager.trackedPluginCount());
        }
    }

    @Test
    void createsWorkingSpringDataRepository() {
        PluginJpaProperties properties = new PluginJpaProperties();
        properties.setDdlAuto("create-drop");
        PluginJpaPersistenceManager manager = new PluginJpaPersistenceManager(
                new DriverManagerDataSource("jdbc:h2:mem:plugin-jpa;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa",
                        ""),
                new HibernateJpaVendorAdapter(), properties);
        try (AnnotationConfigApplicationContext plugin = plugin()) {
            manager.initializeJpaForPlugin(plugin.getId(), "com.pmplugin4j.jpa", plugin);
            plugin.refresh();

            SampleRepository repository = plugin.getBean("jpa.sampleRepository", SampleRepository.class);
            SampleEntity entity = new SampleEntity();
            entity.setId(1L);
            repository.save(entity);

            assertEquals(1, repository.count());
        }
    }

    @Test
    void ignoresDuplicateInitialization() {
        PluginJpaPersistenceManager manager = manager();
        try (AnnotationConfigApplicationContext plugin = plugin()) {
            manager.initializeJpaForPlugin(plugin.getId(), "com.pmplugin4j.jpa", plugin);
            manager.initializeJpaForPlugin(plugin.getId(), "com.pmplugin4j.jpa", plugin);

            assertEquals(1, manager.trackedPluginCount());
        }
    }

    private static PluginJpaPersistenceManager manager() {
        return new PluginJpaPersistenceManager(new DriverManagerDataSource("jdbc:test"),
                new HibernateJpaVendorAdapter(), new PluginJpaProperties());
    }

    private static AnnotationConfigApplicationContext plugin() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId("com.pmplugin4j.jpa");
        return context;
    }
}
