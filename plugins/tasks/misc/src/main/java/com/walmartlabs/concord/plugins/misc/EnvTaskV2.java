package com.walmartlabs.concord.plugins.misc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

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
