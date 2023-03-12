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

import static com.walmartlabs.concord.server.cfg.Utils.getPath;

public class DependenciesConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DependenciesConfiguration.class);

    private final Path cacheDir;

    @Inject
    public DependenciesConfiguration(@Config("dependencies.cacheDir") @Nullable String cacheDir) throws IOException {
        this.cacheDir = getPath(cacheDir, "depsCache");
        log.info("init -> using {} to cache dependencies", this.cacheDir);
    }

    public Path getCacheDir() {
        return cacheDir;
    }
}
