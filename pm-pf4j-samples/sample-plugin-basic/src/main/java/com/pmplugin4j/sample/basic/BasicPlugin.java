package com.pmplugin4j.sample.basic;

import com.pmplugin4j.api.PmPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BasicPlugin extends PmPlugin {

    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context) {
        return context;
    }

    @Override
    protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {
    }
}
