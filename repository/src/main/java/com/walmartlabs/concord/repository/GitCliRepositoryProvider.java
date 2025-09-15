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

import com.walmartlabs.concord.common.GitTokenProvider;
import com.walmartlabs.concord.common.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class GitCliRepositoryProvider implements RepositoryProvider {

    private static final Logger log = LoggerFactory.getLogger(GitCliRepositoryProvider.class);
    private static final String GIT_FILES = "^(\\.git|\\.gitmodules|\\.gitignore)$";

    private final GitClient client;

    public GitCliRepositoryProvider(GitClientConfiguration cfg, GitTokenProvider authProvider) {
        this.client = new GitClient(cfg, authProvider);
    }

    @Override
    public boolean canHandle(String url) {
        return true;
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        RepositoryException lastException = null;

        // try twice
        for (int attemptNo = 0; attemptNo < 2; attemptNo++) {
            if (attemptNo > 0) {
                log.warn("fetch ['{}', '{}', '{}'] -> error: {}, retrying...",
                        request.url(), request.version(), request.destination(), lastException.getMessage());
            }

            try {
                return client.fetch(request);
            } catch (RepositoryException e) {
                lastException = e;
                try {
                    PathUtils.deleteRecursively(request.destination());
                } catch (IOException ee) {
                    log.warn("fetch ['{}', '{}', '{}'] -> cleanup error: {}",
                            request.url(), request.version(), request.destination(), e.getMessage());
                }
            }
        }

        throw lastException;
    }

    @Override
    public Snapshot export(Path src, Path dst, List<String> ignorePatterns) throws IOException {
        LastModifiedSnapshot snapshot = new LastModifiedSnapshot();
        List<String> allIgnorePatterns = new ArrayList<>();
        allIgnorePatterns.add(GIT_FILES);
        allIgnorePatterns.addAll(ignorePatterns);
        PathUtils.copy(src, dst, allIgnorePatterns, snapshot, StandardCopyOption.REPLACE_EXISTING);
        return snapshot;
    }
}
