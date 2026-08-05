package com.pmplugin4j.descriptor;

import org.pf4j.DefaultPluginDescriptor;

/** PF4J descriptor extended with a deterministic plugin startup order. */
public class PmPluginDescriptor extends DefaultPluginDescriptor {

    private int order = 100000;

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
