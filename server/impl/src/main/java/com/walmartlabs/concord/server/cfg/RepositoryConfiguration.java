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
import org.eclipse.sisu.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static com.walmartlabs.concord.server.cfg.Utils.getPath;

public class RepositoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RepositoryConfiguration.class);

    private final Path cacheDir;

    private final Path cacheInfoDir;

    @Inject
    @Config("repositoryCache.concordFileValidationEnabled")
    private boolean concordFileValidationEnabled;

    @Inject
    @Config("repositoryCache.lockTimeout")
    private Duration lockTimeout;

    @Inject
    @Config("repositoryCache.maxAge")
    private Duration maxAge;

    @Inject
    @Config("repositoryCache.lockCount")
    private int lockCount;

    @Inject
    public RepositoryConfiguration(@Config("repositoryCache.cacheDir") @Nullable String cacheDir,
                                   @Config("repositoryCache.cacheInfoDir") @Nullable String cacheInfoDir) throws IOException {

        this.cacheDir = getPath(cacheDir, "repoCache");
        this.cacheInfoDir = getPath(cacheInfoDir, "repoCacheInfo");

        log.info("init -> using {} ({}) to cache repositories", this.cacheDir, this.cacheInfoDir);
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    public Duration getLockTimeout() {
        return lockTimeout;
    }

    public int getLockCount() {
        return lockCount;
    }

    public boolean isConcordFileValidationEnabled() {
        return concordFileValidationEnabled;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public Path getCacheInfoDir() {
        return cacheInfoDir;
    }
}
