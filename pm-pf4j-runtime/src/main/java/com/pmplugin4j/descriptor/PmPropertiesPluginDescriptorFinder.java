package com.pmplugin4j.descriptor;

import java.util.Properties;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginDescriptor;
import org.pf4j.PropertiesPluginDescriptorFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads the standard {@code plugin.properties} descriptor and the optional startup order. */
public class PmPropertiesPluginDescriptorFinder extends PropertiesPluginDescriptorFinder {

    static final String PLUGIN_ORDER = "plugin.order";
    private static final Logger log = LoggerFactory.getLogger(PmPropertiesPluginDescriptorFinder.class);

    @Override
    protected DefaultPluginDescriptor createPluginDescriptorInstance() {
        return new PmPluginDescriptor();
    }

    @Override
    protected PluginDescriptor createPluginDescriptor(Properties properties) {
        PmPluginDescriptor descriptor = (PmPluginDescriptor) super.createPluginDescriptor(properties);
        String orderValue = properties.getProperty(PLUGIN_ORDER);
        if (orderValue != null && !orderValue.isBlank()) {
            try {
                descriptor.setOrder(Integer.parseInt(orderValue.trim()));
            } catch (NumberFormatException exception) {
                log.warn("Invalid plugin.order value '{}', using default {}", orderValue, descriptor.getOrder());
            }
        }
        return descriptor;
    }
}
