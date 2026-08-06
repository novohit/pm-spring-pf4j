package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginStoppedEvent extends PmPluginLifecycleEvent {
    public PmPluginStoppedEvent(Object source, PmPluginDescriptor descriptor) {
        super(source, descriptor);
    }
}
