package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginStartedEvent extends PmPluginLifecycleEvent {
    public PmPluginStartedEvent(Object source, PmPluginDescriptor descriptor) {
        super(source, descriptor);
    }
}
