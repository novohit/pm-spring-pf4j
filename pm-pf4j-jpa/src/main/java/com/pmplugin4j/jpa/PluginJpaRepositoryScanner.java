package com.pmplugin4j.jpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;

final class PluginJpaRepositoryScanner {

    private static final Logger log = LoggerFactory.getLogger(PluginJpaRepositoryScanner.class);

    List<Class<?>> scan(String basePackage, ClassLoader classLoader) {
        log.debug("Scanning JPA repositories in package: {}", basePackage);

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                // Spring's default candidate rule excludes interfaces. Repository candidates are interfaces,
                // so accept every independent type here and let the Repository filter below select them.
                return beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        scanner.setResourceLoader(resolver);

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        List<Class<?>> result = new ArrayList<>();

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> repositoryType = Class.forName(candidate.getBeanClassName(), false, classLoader);
                if (repositoryType.isInterface()) {
                    result.add(repositoryType);
                    log.debug("Found JPA repository: {}", repositoryType.getName());
                }
            } catch (ClassNotFoundException exception) {
                log.warn("Failed to load repository candidate: {}", candidate.getBeanClassName(), exception);
            }
        }

        log.debug("Found {} JPA repository interfaces in package: {}", result.size(), basePackage);
        return result;
    }

    BeanDefinition createRepositoryBeanDefinition(Class<?> repositoryInterface, String entityManagerBeanName,
            String transactionManagerBeanName) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(JpaRepositoryFactoryBean.class);
        builder.addConstructorArgValue(repositoryInterface);
        builder.addPropertyReference("entityManager", entityManagerBeanName);
        builder.addPropertyValue("transactionManager", transactionManagerBeanName);
        builder.setLazyInit(false);
        return builder.getBeanDefinition();
    }
}
