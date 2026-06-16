package com.agileboot.plugin.factory;

import com.agileboot.plugin.api.DefaultPluginContext;
import com.agileboot.plugin.api.PluginContext;
import com.agileboot.plugin.api.PmPlugin;
import com.agileboot.plugin.config.PluginProperties;
import com.agileboot.plugin.config.TenantPluginConfig;
import org.pf4j.DefaultPluginFactory;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Constructor;

/**
 * AgileBoot插件工厂
 *
 * 负责创建插件实例并注入PluginContext
 */
public class PmPluginFactory extends DefaultPluginFactory {

    private static final Logger log = LoggerFactory.getLogger(PmPluginFactory.class);

    private final ApplicationContext applicationContext;
    private final PluginProperties pluginProperties;

    public PmPluginFactory(ApplicationContext applicationContext, PluginProperties pluginProperties) {
        this.applicationContext = applicationContext;
        this.pluginProperties = pluginProperties;
    }

    @Override
    protected Plugin createInstance(Class<?> pluginClass, PluginWrapper pluginWrapper) {
        String pluginId = pluginWrapper.getPluginId();

        // 创建PluginContext
        PluginContext context = createPluginContext(pluginId);

        try {
            // 尝试使用 PmPlugin(PluginWrapper, PluginContext) 构造函数
            Constructor<?> constructor = pluginClass.getConstructor(PluginWrapper.class, PluginContext.class);
            return (Plugin) constructor.newInstance(pluginWrapper, context);
        } catch (NoSuchMethodException e) {
            // 如果没有该构造函数，使用默认构造函数
            log.debug("[{}] 插件未使用PmPlugin基类，使用默认构造函数", pluginId);
            return super.createInstance(pluginClass, pluginWrapper);
        } catch (Exception e) {
            log.error("[{}] 创建插件实例失败: {}", pluginId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建插件上下文
     */
    private PluginContext createPluginContext(String pluginId) {
        String tenantId = pluginProperties.getCurrentTenant();
        TenantPluginConfig.PluginInstanceConfig pluginConfig = null;

        if (tenantId != null) {
            pluginConfig = pluginProperties.getPluginConfig(pluginId, tenantId);
        }

        return new DefaultPluginContext(pluginId, applicationContext, pluginConfig);
    }
}
