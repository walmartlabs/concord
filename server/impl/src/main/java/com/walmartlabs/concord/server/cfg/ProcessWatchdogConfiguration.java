package com.walmartlabs.concord.server.cfg;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;

@Named
@Singleton
public class ProcessWatchdogConfiguration implements Serializable {

    private static final String MAX_FAILURE_HANDLING_AGE_KEY = "MAX_FAILURE_HANDLING_AGE";
    private static final String MAX_STALLED_AGE_KEY = "MAX_STALLED_AGE";
    private static final String MAX_START_FAILURE_AGE_KEY = "MAX_START_FAILURE_AGE";

    private final String maxFailureHandlingAge;
    private final String maxStalledAge;
    private final String maxStartFailureAge;

    public ProcessWatchdogConfiguration() {
        this.maxFailureHandlingAge = Utils.getEnv(MAX_FAILURE_HANDLING_AGE_KEY, "3 days");
        this.maxStalledAge = Utils.getEnv(MAX_STALLED_AGE_KEY, "1 minute");
        this.maxStartFailureAge = Utils.getEnv(MAX_START_FAILURE_AGE_KEY, "10 minutes");
    }

    public String getMaxFailureHandlingAge() {
        return maxFailureHandlingAge;
    }

    public String getMaxStalledAge() {
        return maxStalledAge;
    }

    public String getMaxStartFailureAge() {
        return maxStartFailureAge;
    }
}
