package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginBeforeUnloadEvent extends PmPluginLifecycleEvent {
    public PmPluginBeforeUnloadEvent(PmPluginDescriptor descriptor) {
        super(descriptor.getPluginId(), descriptor);
    }
}
