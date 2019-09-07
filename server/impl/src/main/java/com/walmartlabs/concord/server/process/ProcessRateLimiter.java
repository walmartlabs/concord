package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.ExtraStatus;
import com.walmartlabs.concord.server.RateLimiter;
import com.walmartlabs.concord.server.cfg.ProcessQueueConfiguration;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Just a wrapper for {@link RateLimiter} with a timer.
 */
@Named
@Singleton
public class ProcessRateLimiter {

    private final ProcessQueueConfiguration cfg;
    private final RateLimiter limiter;

    @Inject
    public ProcessRateLimiter(ProcessQueueConfiguration cfg) {
        this.cfg = cfg;

        int rate = cfg.getRateLimit();
        this.limiter = rate > 0 ? new RateLimiter(rate) : null;
    }

    // the timer's values reflect how much time a request spent waiting for "a permit"
    @WithTimer
    public void process(Payload payload) {
        if (this.limiter == null) {
            return;
        }

        int timeout = cfg.getMaxRateTimeout();
        if (!limiter.tryAcquire(timeout)) {
            throw new ProcessException(payload.getProcessKey(), "Request timeout while being rate limited", ExtraStatus.TOO_MANY_REQUESTS);
        }
    }
}
