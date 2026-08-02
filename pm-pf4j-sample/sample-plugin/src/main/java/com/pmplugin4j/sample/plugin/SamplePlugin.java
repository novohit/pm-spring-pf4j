package com.pmplugin4j.sample.plugin;

import com.pmplugin4j.api.PluginContext;
import com.pmplugin4j.api.PmPlugin;
import org.pf4j.PluginWrapper;

public class SamplePlugin extends PmPlugin {

    public SamplePlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper, context);
    }
}
