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
import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.agent.cfg.Utils.getDir;

@Named
@Singleton
public class RepositoryCacheConfiguration {

    private final Path cacheDir;
    private final long lockTimeout;
    private final int lockCount;
    private final long maxAge;
    private final Path infoDir;

    @Inject
    public RepositoryCacheConfiguration(Config cfg) {
        this.cacheDir = getDir(cfg, "repositoryCache.cacheDir");
        this.lockTimeout = cfg.getDuration("repositoryCache.lockTimeout", TimeUnit.MILLISECONDS);
        this.lockCount = cfg.getInt("repositoryCache.lockCount");
        this.maxAge = cfg.getDuration("repositoryCache.maxAge", TimeUnit.MILLISECONDS);
        this.infoDir = getDir(cfg, "repositoryCache.cacheInfoDir");
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    public long getLockTimeout() {
        return lockTimeout;
    }

    public int getLockCount() {
        return lockCount;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public Path getInfoDir() {
        return infoDir;
    }
}
