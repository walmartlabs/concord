package com.walmartlabs.concord.repository;

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

import com.walmartlabs.concord.sdk.Secret;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RepositoryProviders {

    private final List<RepositoryProvider> providers;

    public RepositoryProviders(List<RepositoryProvider> providers) {
        this.providers = providers;
    }

    public Repository fetch(String url, String branch, String commitId, String path, Secret secret, Path destDir) {
        RepositoryProvider provider = getProvider(url);
        String fetchedCommitId = provider.fetch(url, branch, commitId, secret, destDir);

        Path repoPath = repoPath(destDir, path);

        return new Repository(provider.getBranchOrDefault(branch), destDir, repoPath, fetchedCommitId, provider);
    }

    private RepositoryProvider getProvider(String url) {
        return providers.stream()
                .filter(p -> p.canHandle(url))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find provider for '" + url + "'"));
    }

    private static String normalizePath(String s) {
        if (s == null) {
            return null;
        }

        while (s.startsWith("/")) {
            s = s.substring(1);
        }

        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }

        if (s.trim().isEmpty()) {
            return null;
        }

        return s;
    }

    private static Path repoPath(Path baseDir, String p) {
        String normalized = normalizePath(p);
        if (normalized == null) {
            return baseDir;
        }

        Path repoDir = baseDir.resolve(normalized);
        if (!Files.exists(repoDir)) {
            throw new RepositoryException("Invalid repository path: '" + p + "' doesn't exist");
        } else if (!repoDir.toFile().isDirectory()) {
            throw new RepositoryException("Invalid repository path: '" + p + "' must be a valid directory");
        }

        return repoDir;
    }
}
