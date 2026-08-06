package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginDisabledEvent extends PmPluginLifecycleEvent {
    public PmPluginDisabledEvent(Object source, PmPluginDescriptor descriptor) {
        super(source, descriptor);
    }
}
