package com.pmplugin4j.sample.security;

import com.pmplugin4j.api.PmPlugin;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class SecurityPlugin extends PmPlugin {

    @Override
    protected AnnotationConfigApplicationContext beforeApplicationContextRefresh(
            AnnotationConfigApplicationContext context) {
        return context;
    }

    @Override
    protected void afterApplicationContextReady(AnnotationConfigApplicationContext context) {
    }
}
