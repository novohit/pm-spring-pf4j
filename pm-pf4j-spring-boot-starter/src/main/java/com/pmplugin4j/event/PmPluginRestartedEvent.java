package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginRestartedEvent extends PmPluginLifecycleEvent {
    public PmPluginRestartedEvent(PmPluginDescriptor descriptor) {
        super(descriptor.getPluginId(), descriptor);
    }
}
