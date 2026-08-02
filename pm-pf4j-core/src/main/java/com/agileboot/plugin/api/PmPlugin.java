package com.agileboot.plugin.api;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AgileBoot插件基类
 *
 * 所有业务插件必须继承此类
 * 通过构造函数注入PluginContext，提供访问宿主应用的能力
 */
public abstract class PmPlugin extends Plugin {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 插件上下文（由PmPluginFactory注入）
     */
    protected final PluginContext context;

    /**
     * 构造函数 - 由PmPluginFactory调用
     *
     * @param wrapper PF4J插件包装器
     * @param context 插件上下文
     */
    public PmPlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper);
        this.context = context;
    }

    /**
     * 获取插件上下文
     */
    protected PluginContext getContext() {
        return context;
    }

    /**
     * 获取服务（便捷方法）
     */
    protected <T> T getService(Class<T> serviceClass) {
        return context.getService(serviceClass);
    }
}
