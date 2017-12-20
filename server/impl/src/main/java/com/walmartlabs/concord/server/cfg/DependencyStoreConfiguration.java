package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class DependencyStoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DependencyStoreConfiguration.class);

    public static final String DEPS_STORE_DIR_KEY = "DEPS_STORE_DIR";

    private final Path depsDir;

    public DependencyStoreConfiguration() {
        String s = System.getenv(DEPS_STORE_DIR_KEY);
        if (s == null) {
            log.warn("init -> {} must be set in order to use project templates and custom process dependencies", DEPS_STORE_DIR_KEY);
        }

        this.depsDir = s != null ? Paths.get(s).toAbsolutePath() : null;
        log.info("init -> depsDir: {}", depsDir);
    }

    public DependencyStoreConfiguration(Path depsDir) {
        this.depsDir = depsDir;
    }

    public Path getDepsDir() {
        return depsDir;
    }
}
