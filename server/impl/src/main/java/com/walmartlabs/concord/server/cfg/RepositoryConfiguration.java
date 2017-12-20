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
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class RepositoryConfiguration implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(RepositoryConfiguration.class);

    public static final String REPO_CACHE_DIR_KEY = "REPO_CACHE_DIR";
    public static final String REPO_META_DIR_KEY = "REPO_META_DIR";

    private final Path repoCacheDir;
    private final Path repoMetaDir;

    public RepositoryConfiguration() throws IOException {
        this.repoCacheDir = getEnvOrTemp(REPO_CACHE_DIR_KEY, "repos");
        this.repoMetaDir = getEnvOrTemp(REPO_META_DIR_KEY, "repos_meta");

        log.info("init -> repoCacheDir: {}, repoMetaDir: {}", repoCacheDir, repoMetaDir);
    }

    public Path getRepoCacheDir() {
        return repoCacheDir;
    }

    public Path getRepoMetaDir() {
        return repoMetaDir;
    }

    private static Path getEnvOrTemp(String key, String tempPrefix) throws IOException {
        String s = System.getenv(REPO_CACHE_DIR_KEY);
        return s != null ? Paths.get(s).toAbsolutePath() : Files.createTempDirectory(tempPrefix);
    }
}
