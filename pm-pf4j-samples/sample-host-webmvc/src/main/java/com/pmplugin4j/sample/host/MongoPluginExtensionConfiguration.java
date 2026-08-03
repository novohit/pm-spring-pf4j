package com.pmplugin4j.sample.host;

import com.pmplugin4j.lifecycle.PluginLifecyclePhase;
import com.pmplugin4j.lifecycle.PluginResourceRegistrar;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Example of a host-owned infrastructure bridge for plugin contexts. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MongoTemplate.class)
public class MongoPluginExtensionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MongoPluginExtensionConfiguration.class);

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    PluginResourceRegistrar mongoPluginRegistrar() {
        return new PluginResourceRegistrar() {
            @Override
            public Set<PluginLifecyclePhase> phases() {
                return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
            }

            @Override
            public int order() {
                return 6;
            }

            @Override
            public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginContext) {
                MongoTemplate hostTemplate = pluginContext.getParent().getBean(MongoTemplate.class);
                MongoTemplate pluginTemplate = new MongoTemplate(hostTemplate.getMongoDatabaseFactory());
                pluginContext.getBeanFactory().registerSingleton("mongoTemplate", pluginTemplate);
                log.info("Plugin {} received isolated MongoTemplate: host={}, plugin={}", pluginContext.getId(),
                        System.identityHashCode(hostTemplate), System.identityHashCode(pluginTemplate));
            }
        };
    }
}
