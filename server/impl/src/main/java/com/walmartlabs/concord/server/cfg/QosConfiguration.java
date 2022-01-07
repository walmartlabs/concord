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

import com.walmartlabs.ollie.config.Config;

import javax.inject.Inject;
import java.io.Serializable;
import java.time.Duration;

public class QosConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("qos.maxRequests")
    public int maxRequests;

    @Inject
    @Config("qos.maxWait")
    public Duration maxWait;

    @Inject
    @Config("qos.suspend")
    public Duration suspend;

    public int getMaxRequests() {
        return maxRequests;
    }

    public Duration getMaxWait() {
        return maxWait;
    }

    public Duration getSuspend() {
        return suspend;
    }
}
