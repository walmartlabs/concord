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

import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.JsonStorageDB;
import com.walmartlabs.concord.config.Config;

import javax.inject.Inject;
import java.time.Duration;

@JsonStorageDB
public class JsonStorageDBConfiguration implements DatabaseConfiguration {

    @Inject
    @Config("db.url")
    private String url;

    @Inject
    @Config("db.inventoryUsername")
    private String username;

    @Inject
    @Config("db.inventoryPassword")
    private String password;

    @Inject
    @Config("db.maxPoolSize")
    private int maxPoolSize;

    @Inject
    @Config("db.maxLifetime")
    private Duration maxLifetime;

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
        return maxLifetime;
    }
}
