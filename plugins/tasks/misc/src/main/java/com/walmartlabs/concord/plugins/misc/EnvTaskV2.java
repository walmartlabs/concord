package com.walmartlabs.concord.plugins.misc;

import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Named;

/**
 * Provides access to environment variables.
 */
@Named("env")
@DryRunReady
public class EnvTaskV2 implements Task {

    /**
     * Retrieves the value of the environment variable for the given key.
     * If the variable is not set, returns {@code null}.
     *
     * @param key the name of the environment variable to retrieve
     * @return the value of the environment variable, or {@code null} if not set
     */
    public String get(String key) {
        return getOrDefault(key, null);
    }

    /**
     * Retrieves the value of the environment variable for the given key.
     * If the variable is not set, returns the specified default value.
     *
     * @param key the name of the environment variable to retrieve
     * @param defaultValue the value to return if the environment variable is not set
     * @return the value of the environment variable, or the default value if not set
     */
    public String getOrDefault(String key, String defaultValue) {
        String result = System.getenv(key);
        if (result != null) {
            return result;
        }
        return defaultValue;
    }
}
