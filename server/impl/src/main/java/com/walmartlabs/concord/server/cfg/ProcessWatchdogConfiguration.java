package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
