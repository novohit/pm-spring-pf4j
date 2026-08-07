package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginAfterInstallEvent extends PmPluginLifecycleEvent {
    public PmPluginAfterInstallEvent(PmPluginDescriptor descriptor) {
        super(descriptor.getPluginId(), descriptor);
    }
}
