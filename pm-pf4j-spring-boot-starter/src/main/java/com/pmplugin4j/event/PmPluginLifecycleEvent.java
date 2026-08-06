package com.pmplugin4j.event;

import com.pmplugin4j.descriptor.PmPluginDescriptor;
import java.io.Serial;
import org.springframework.context.ApplicationEvent;

/** Common base for events associated with one plugin descriptor. */
public abstract class PmPluginLifecycleEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PmPluginDescriptor descriptor;

    protected PmPluginLifecycleEvent(Object source, PmPluginDescriptor descriptor) {
        super(source);
        this.descriptor = descriptor;
    }

    public PmPluginDescriptor getPluginDescriptor() {
        return descriptor;
    }

    public String getPluginId() {
        return descriptor.getPluginId();
    }
}
