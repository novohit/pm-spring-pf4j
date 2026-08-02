package com.pmplugin4j.mybatis;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class PluginMybatisRegistrarTest {

    @Test
    void registersPluginOwnedTemplateAndMapperScanner() {
        SqlSessionFactory sessionFactory = mock(SqlSessionFactory.class);
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), mock(DataSource.class)));
        when(sessionFactory.getConfiguration()).thenReturn(configuration);
        try (AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
                AnnotationConfigApplicationContext plugin = new AnnotationConfigApplicationContext()) {
            host.getBeanFactory().registerSingleton("sqlSessionFactory", sessionFactory);
            host.refresh();
            plugin.setId("com.example.plugin");
            plugin.setParent(host);
            plugin.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("plugin", Map.of("pm.plugin.base-package", "com.example.plugin")));

            new PluginMybatisRegistrar().onBeforeContextRefresh(plugin);

            assertTrue(plugin.containsBean("com.example.plugin_sqlSessionTemplate"));
            assertTrue(plugin.containsBeanDefinition("com.example.plugin_mapperScanner"));
        }
    }
}
