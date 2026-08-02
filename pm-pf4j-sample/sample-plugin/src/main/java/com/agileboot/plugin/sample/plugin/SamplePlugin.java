package com.agileboot.plugin.sample.plugin;

import com.agileboot.plugin.api.PluginContext;
import com.agileboot.plugin.api.PmPlugin;
import org.pf4j.PluginWrapper;

public class SamplePlugin extends PmPlugin {

    public SamplePlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper, context);
    }
}
