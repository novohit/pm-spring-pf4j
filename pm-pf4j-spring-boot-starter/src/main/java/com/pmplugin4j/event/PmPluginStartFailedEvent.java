package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;

public class PmPluginStartFailedEvent extends PmPluginLifecycleEvent {
    private final PmPluginStartingError error;

    public PmPluginStartFailedEvent(Object source, PmPluginDescriptor descriptor, PmPluginStartingError error) {
        super(source, descriptor);
        this.error = error;
    }

    public PmPluginStartingError getError() {
        return error;
    }
}
