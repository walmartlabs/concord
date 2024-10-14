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

import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.server.plugins.noderoster.db.NodeRosterDB;
import com.walmartlabs.concord.config.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;

@Named
@NodeRosterDB
@Singleton
public class NodeRosterDBConfiguration implements DatabaseConfiguration {

    @Inject
    @Config("noderoster.db.url")
    private String url;

    @Inject
    @Config("noderoster.db.username")
    private String username;

    @Inject
    @Config("noderoster.db.password")
    private String password;

    @Inject
    @Config("noderoster.db.maxPoolSize")
    private int maxPoolSize;

    @Override
    public String url() {
        return url;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public int maxPoolSize() {
        return maxPoolSize;
    }

    @Override
    public Duration maxLifetime() {
        return Duration.ofSeconds(0);
    }
}
