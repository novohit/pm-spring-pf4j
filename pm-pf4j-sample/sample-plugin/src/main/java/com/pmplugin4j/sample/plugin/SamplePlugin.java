package com.pmplugin4j.sample.plugin;

import com.pmplugin4j.api.PmPlugin;
import org.pf4j.PluginWrapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class SamplePlugin extends PmPlugin {

    public SamplePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context) {
        return context;
    }

    @Override
    protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {}
}
