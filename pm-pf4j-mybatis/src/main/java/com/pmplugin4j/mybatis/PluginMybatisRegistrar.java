package com.pmplugin4j.mybatis;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Registers plugin-owned MyBatis resources in a plugin application context. */
public final class PluginMybatisRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PluginMybatisRegistrar.class);

    private final ApplicationContext hostApplicationContext;

    public PluginMybatisRegistrar(ApplicationContext hostApplicationContext) {
        this.hostApplicationContext = hostApplicationContext;
    }

    public void register(
            String pluginId,
            String mapperPackage,
            AnnotationConfigApplicationContext pluginApplicationContext) {
        try {
            SqlSessionFactory sqlSessionFactory =
                    hostApplicationContext.getBean(SqlSessionFactory.class);
            DefaultListableBeanFactory beanFactory =
                    (DefaultListableBeanFactory) pluginApplicationContext.getBeanFactory();

            String templateBeanName = pluginId + "_sqlSessionTemplate";
            beanFactory.registerSingleton(
                    templateBeanName, new SqlSessionTemplate(sqlSessionFactory));

            BeanDefinitionBuilder scanner =
                    BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
            scanner.addPropertyValue("basePackage", mapperPackage);
            scanner.addPropertyValue("sqlSessionTemplateBeanName", templateBeanName);
            beanFactory.registerBeanDefinition(
                    pluginId + "_mapperScanner", scanner.getBeanDefinition());
            log.info("[{}] Registered MyBatis mapper package: {}", pluginId, mapperPackage);
        } catch (Exception exception) {
            log.warn("[{}] MyBatis registration skipped: {}", pluginId, exception.getMessage());
        }
    }
}
