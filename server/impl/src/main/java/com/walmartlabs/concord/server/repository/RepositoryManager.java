package com.walmartlabs.concord.server.repository;

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


import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.project.RepositoryException;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;

public interface RepositoryManager {

    String DEFAULT_BRANCH = "master";

    void testConnection(UUID orgId, String uri, String branch, String commitId, String path, String secretName);

    Path fetch(UUID projectId, RepositoryEntry repository) throws RepositoryException;

    RepositoryInfo getInfo(RepositoryEntry repository, Path path);

    <T> T withLock(UUID projectId, String repoName, Callable<T> f);

    Path getRepoPath(UUID projectId, RepositoryEntry repository);

    class RepositoryInfo {

        private final String commitId;

        private final String message;

        private final String author;

        public RepositoryInfo(String commitId, String message, String author) {
            this.commitId = commitId;
            this.message = message;
            this.author = author;
        }

        public String getCommitId() {
            return commitId;
        }

        public String getMessage() {
            return message;
        }

        public String getAuthor() {
            return author;
        }

        @Override
        public String toString() {
            return "RepositoryInfo{" +
                    "commitId='" + commitId + '\'' +
                    ", message='" + message + '\'' +
                    ", author='" + author + '\'' +
                    '}';
        }
    }
}
