package com.walmartlabs.concord.agent.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.typesafe.config.Config;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class PreForkConfiguration {

    private final boolean enabled;
    private final long maxAge;
    private final int maxCount;

    @Inject
    public PreForkConfiguration(Config cfg) {
        this.enabled = cfg.getBoolean("prefork.enabled");
        this.maxAge = cfg.getDuration("prefork.maxAge", TimeUnit.MILLISECONDS);
        this.maxCount = cfg.getInt("prefork.maxCount");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public int getMaxCount() {
        return maxCount;
    }
}
