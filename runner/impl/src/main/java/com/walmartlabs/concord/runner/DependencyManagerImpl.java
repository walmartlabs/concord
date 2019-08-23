package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyEntity;
import com.walmartlabs.concord.runner.model.RunnerConfiguration;
import com.walmartlabs.concord.sdk.DependencyManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class DependencyManagerImpl implements DependencyManager {

    private final com.walmartlabs.concord.dependencymanager.DependencyManager dependencyManager;

    @Inject
    public DependencyManagerImpl(RunnerConfiguration cfg) throws IOException {
        Path cacheDir = getCacheDir(cfg);
        this.dependencyManager = new com.walmartlabs.concord.dependencymanager.DependencyManager(cacheDir);
    }

    @Override
    public Path resolve(URI uri) throws IOException {
        DependencyEntity e = dependencyManager.resolveSingle(uri);
        return e.getPath();
    }

    private static Path getCacheDir(RunnerConfiguration cfg) {
        try {
            String s = cfg.dependencyManager().cacheDir();
            if (s == null) {
                return IOUtils.createTempDir("dependencyCache");
            }

            Path p = Paths.get(s);
            if (!Files.exists(p) || !Files.isDirectory(p)) {
                throw new RuntimeException("The dependency cache directory doesn't exist or not a directory: " + p);
            }

            return p;
        } catch (IOException e) {
            throw new RuntimeException("Error while creating the dependency cache directory", e);
        }
    }
}
