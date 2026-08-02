package com.pmplugin4j.factory;

import com.pmplugin4j.api.DefaultPluginContext;
import com.pmplugin4j.api.PluginContext;
import com.pmplugin4j.api.PmPlugin;
import com.pmplugin4j.config.PluginProperties;
import com.pmplugin4j.config.TenantPluginConfig;
import com.pmplugin4j.mybatis.PluginMybatisRegistrar;
import org.pf4j.DefaultPluginFactory;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PmPluginFactory extends DefaultPluginFactory {

    private static final Logger log = LoggerFactory.getLogger(PmPluginFactory.class);

    private final ApplicationContext hostApplicationContext;
    private final PluginProperties pluginProperties;
    private final Map<String, DefaultPluginContext> pluginContexts = new ConcurrentHashMap<>();

    public PmPluginFactory(ApplicationContext applicationContext, PluginProperties pluginProperties) {
        this.hostApplicationContext = applicationContext;
        this.pluginProperties = pluginProperties;
    }

    public Map<String, DefaultPluginContext> getPluginContexts() {
        return pluginContexts;
    }

    @Override
    protected Plugin createInstance(Class<?> pluginClass, PluginWrapper pluginWrapper) {
        String pluginId = pluginWrapper.getPluginId();
        try {
            AnnotationConfigApplicationContext pluginAC = createPluginApplicationContext(pluginId, pluginWrapper);
            PluginContext context = createPluginContext(pluginId, pluginAC);
            try {
                Constructor<?> constructor = pluginClass.getConstructor(PluginWrapper.class, PluginContext.class);
                return (Plugin) constructor.newInstance(pluginWrapper, context);
            } catch (NoSuchMethodException e) {
                log.debug("[{}] 插件未使用PmPlugin基类，使用默认构造函数", pluginId);
                return super.createInstance(pluginClass, pluginWrapper);
            }
        } catch (Exception e) {
            log.error("[{}] 创建插件实例失败: {}", pluginId, e.getMessage(), e);
            return null;
        }
    }

    private AnnotationConfigApplicationContext createPluginApplicationContext(String pluginId, PluginWrapper pluginWrapper) {
        AnnotationConfigApplicationContext pluginAC = new AnnotationConfigApplicationContext();
        pluginAC.setParent(hostApplicationContext);
        pluginAC.setClassLoader(pluginWrapper.getPluginClassLoader());
        pluginAC.setBeanNameGenerator(new PluginBeanNameGenerator(pluginId));

        String pluginClassName = pluginWrapper.getDescriptor().getPluginClass();
        String pluginPackage = pluginClassName.substring(0, pluginClassName.lastIndexOf('.'));
        pluginAC.scan(pluginPackage);
        log.info("[{}] 扫描插件包: {}", pluginId, pluginPackage);

        new PluginMybatisRegistrar(hostApplicationContext)
                .register(pluginId, pluginPackage + ".db.mapper", pluginAC);
        registerMongoRepositories(pluginId, pluginPackage, pluginAC);

        pluginAC.refresh();
        log.info("[{}] 插件ApplicationContext创建完成，{}个Bean",
                pluginId, pluginAC.getBeanDefinitionCount());
        return pluginAC;
    }

    private void registerMongoRepositories(String pluginId, String pluginPackage, AnnotationConfigApplicationContext pluginAC) {
        try {
            MongoTemplate mongoTemplate = hostApplicationContext.getBean(MongoTemplate.class);
            String documentPackage = pluginPackage + ".document";
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) pluginAC.getBeanFactory();
            ClassLoader pluginCL = pluginAC.getClassLoader();

            int count = 0;
            String prefix = documentPackage.replace('.', '/') + "/";
            java.util.Enumeration<java.net.URL> resources = pluginCL.getResources(prefix);
            while (resources.hasMoreElements()) {
                java.net.URL url = resources.nextElement();
                java.util.List<String> classNames = new java.util.ArrayList<>();

                if (url.toString().startsWith("jar:")) {
                    String jarPath = url.toString().substring(4, url.toString().indexOf("!/"));
                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new java.io.File(new java.net.URI(jarPath)))) {
                        java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                                classNames.add(documentPackage + "." + entry.getName().substring(prefix.length()).replace(".class", "").replace('/', '.'));
                            }
                        }
                    }
                } else if ("file".equals(url.getProtocol())) {
                    java.io.File dir = new java.io.File(url.toURI());
                    if (dir.isDirectory()) {
                        for (java.io.File file : dir.listFiles()) {
                            if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                                classNames.add(documentPackage + "." + file.getName().replace(".class", ""));
                            }
                        }
                    }
                }

                for (String className : classNames) {
                    Class<?> clazz = pluginCL.loadClass(className);
                    if (!clazz.isInterface()) continue;
                    boolean isRepo = false;
                    for (Class<?> iface : clazz.getInterfaces()) {
                        if (iface.getName().contains("Repository")) { isRepo = true; break; }
                    }
                    if (!isRepo) continue;

                    ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(pluginCL);
                    try {
                        Object proxy = java.lang.reflect.Proxy.newProxyInstance(pluginCL,
                                new Class[]{clazz},
                                new MongoRepositoryProxyHandler(mongoTemplate, clazz));
                        beanFactory.registerSingleton(clazz.getSimpleName(), proxy);
                    } finally {
                        Thread.currentThread().setContextClassLoader(originalCL);
                    }
                    count++;
                    log.info("[{}] 注册MongoDB Repository: {}", pluginId, clazz.getSimpleName());
                }
            }
            if (count > 0) {
                log.info("[{}] MongoDB Repository注册完成（{}个）", pluginId, count);
            }
        } catch (Exception e) {
            log.warn("[{}] MongoDB Repository注册失败: {}", pluginId, e.getMessage());
        }
    }

    private PluginContext createPluginContext(String pluginId, AnnotationConfigApplicationContext pluginAC) {
        String tenantId = pluginProperties.getCurrentTenant();
        TenantPluginConfig.PluginInstanceConfig pluginConfig = null;
        if (tenantId != null) {
            pluginConfig = pluginProperties.getPluginConfig(pluginId, tenantId);
        }
        DefaultPluginContext context = new DefaultPluginContext(pluginId, pluginAC, pluginConfig);
        pluginContexts.put(pluginId, context);
        return context;
    }
}
