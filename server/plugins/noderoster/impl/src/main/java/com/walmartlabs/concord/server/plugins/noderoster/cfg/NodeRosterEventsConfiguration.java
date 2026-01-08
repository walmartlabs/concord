package com.walmartlabs.concord.server.plugins.noderoster.cfg;

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
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

@Named
@Singleton
public class NodeRosterEventsConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("noderoster.events.period")
    private Duration period;

    @Inject
    @Config("noderoster.events.fetchLimit")
    private int fetchLimit;

    @Inject
    @Config("noderoster.events.hostCacheSize")
    private long hostCacheSize;

    @Inject
    @Config("noderoster.events.hostCacheDuration")
    private Duration hostCacheDuration;

    private final Instant startTimestamp;

    @Inject
    public NodeRosterEventsConfiguration(@Config("noderoster.events.startTimestamp") @Nullable String startTimestamp) {
        this.startTimestamp = startTimestamp != null ? Instant.parse(startTimestamp) : null;
    }

    public Duration getPeriod() {
        return period;
    }

    public int getFetchLimit() {
        return fetchLimit;
    }

    public long getHostCacheSize() {
        return hostCacheSize;
    }

    public Duration getHostCacheDuration() {
        return hostCacheDuration;
    }

    @Nullable
    public Instant getStartTimestamp() {
        return startTimestamp;
    }
}
