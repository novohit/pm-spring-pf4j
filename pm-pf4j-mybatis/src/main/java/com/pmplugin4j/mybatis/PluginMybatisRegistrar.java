package com.pmplugin4j.mybatis;

import com.pmplugin4j.lifecycle.BuiltInPluginResourceRegistrar;
import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import java.util.Set;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Registers plugin-owned MyBatis resources in a plugin application context. */
public final class PluginMybatisRegistrar implements BuiltInPluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PluginMybatisRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginApplicationContext) {
        String pluginId = pluginApplicationContext.getId();
        String basePackage = pluginApplicationContext.getEnvironment().getRequiredProperty("pm.plugin.base-package");
        SqlSessionFactory sqlSessionFactory = pluginApplicationContext.getParent()
            .getBeanProvider(SqlSessionFactory.class)
            .getIfAvailable();
        if (sqlSessionFactory == null) {
            log.debug("[{}] MyBatis is not available, skipping mapper registration", pluginId);
            return;
        }
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) pluginApplicationContext.getBeanFactory();

        String templateBeanName = pluginId + "_sqlSessionTemplate";
        beanFactory.registerSingleton(templateBeanName, new SqlSessionTemplate(sqlSessionFactory));

        BeanDefinitionBuilder scanner = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
        scanner.addPropertyValue("basePackage", basePackage + ".db.mapper");
        scanner.addPropertyValue("sqlSessionTemplateBeanName", templateBeanName);
        beanFactory.registerBeanDefinition(pluginId + "_mapperScanner", scanner.getBeanDefinition());
        log.info("[{}] Registered MyBatis mapper package: {}", pluginId, basePackage);
    }
}
