package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.config.Config;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.Serializable;
import java.time.Duration;

public class AuditConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("audit.enabled")
    private boolean enabled;

    @Inject
    @Config("audit.cleanupPeriod")
    private Duration period;

    @Inject
    @Config("audit.maxLogAge")
    private Duration maxLogAge;

    @Nullable
    @Inject
    @Config("audit.maxSearchInterval")
    private Duration maxSearchInterval;

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getPeriod() {
        return period;
    }

    public Duration getMaxLogAge() {
        return maxLogAge;
    }

    public Duration getMaxSearchInterval() {
        return maxSearchInterval;
    }
}
