package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginStartingEvent extends PmPluginLifecycleEvent {
    public PmPluginStartingEvent(Object source, PmPluginDescriptor descriptor) {
        super(source, descriptor);
    }
}
