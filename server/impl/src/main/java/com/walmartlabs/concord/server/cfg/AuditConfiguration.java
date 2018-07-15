package com.walmartlabs.concord.server.cfg;

import com.walmartlabs.ollie.config.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;

@Named
@Singleton
public class AuditConfiguration implements Serializable {

    @Inject
    @Config("audit.enabled")
    private boolean enabled;

    @Inject
    @Config("audit.maxLogAge")
    private long maxLogAge;

    public boolean isEnabled() {
        return enabled;
    }

    public long getMaxLogAge() {
        return maxLogAge;
    }
}
