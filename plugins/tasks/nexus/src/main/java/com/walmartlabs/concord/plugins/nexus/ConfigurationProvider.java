package com.walmartlabs.concord.plugins.nexus;

import javax.inject.Named;
import javax.inject.Provider;

@Named
public class ConfigurationProvider implements Provider<Configuration> {

    @Override
    public Configuration get() {
        return new Configuration("snapshots", ".*server-(.*)\\.tar.gz");
    }
}
