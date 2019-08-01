package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.repository.Repository;
import com.walmartlabs.concord.repository.Snapshot;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.keys.HeaderKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.repository.RepositoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adds repository files to a payload.
 */
@Named
public class RepositoryProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(RepositoryProcessor.class);

    /**
     * Repository effective parameters.
     */
    public static final HeaderKey<RepositoryInfo> REPOSITORY_INFO_KEY = HeaderKey.register("_repositoryInfo", RepositoryInfo.class);
    public static final HeaderKey<List<Snapshot>> REPOSITORY_SNAPSHOT = HeaderKey.registerList("_repositorySnapshot");

    private final RepositoryDao repositoryDao;
    private final RepositoryManager repositoryManager;
    private final LogManager logManager;

    @Inject
    public RepositoryProcessor(RepositoryDao repositoryDao,
                               RepositoryManager repositoryManager,
                               LogManager logManager) {

        this.repositoryDao = repositoryDao;
        this.repositoryManager = repositoryManager;
        this.logManager = logManager;
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, final Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        RepositoryEntry repo = getRepositoryEntry(payload);
        if (projectId == null || repo == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Copying the repository's data: {}", repo);

        Path dst = payload.getHeader(Payload.WORKSPACE_DIR);

        Payload newPayload = repositoryManager.withLock(repo.getUrl(), () -> {
            try {
                Repository repository = repositoryManager.fetch(projectId, repo);
                Snapshot snapshot = repository.export(dst);
                com.walmartlabs.concord.repository.RepositoryInfo info = repository.info();
                String branch = repository.branch();

                CommitInfo ci = null;
                if (info != null) {
                    ci = new CommitInfo(info.getCommitId(), info.getAuthor(), info.getMessage());
                }

                RepositoryInfo i = new RepositoryInfo(repo.getId(), repo.getName(), repo.getUrl(), repo.getPath(), branch, repo.getCommitId(), ci);
                return payload.putHeader(REPOSITORY_INFO_KEY, i)
                        .putHeader(REPOSITORY_SNAPSHOT, Collections.singletonList(snapshot));
            } catch (Exception e) {
                log.error("process -> repository error", e);
                logManager.error(processKey, "Error while processing a repository: " + repo.getUrl(), e);
                throw new ProcessException(processKey, "Error while processing a repository: " + repo.getUrl(), e);
            }
        });

        return chain.process(newPayload);
    }

    @SuppressWarnings("unchecked")
    private RepositoryEntry getRepositoryEntry(Payload payload) {
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID repoId = payload.getHeader(Payload.REPOSITORY_ID);

        if (projectId == null || repoId == null) {
            return null;
        }

        RepositoryEntry repo = repositoryDao.get(projectId, repoId);
        if (repo == null) {
            return null;
        }

        Map<String, Object> cfg = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (cfg != null) {
            String branchOrTag = (String) cfg.getOrDefault(Constants.Request.REPO_BRANCH_OR_TAG, repo.getBranch());
            String commitId = (String) cfg.getOrDefault(Constants.Request.REPO_COMMIT_ID, repo.getCommitId());
            repo = new RepositoryEntry(repo, branchOrTag, commitId);
        }

        return repo;
    }

    public static final class RepositoryInfo implements Serializable {

        private final UUID id;
        private final String name;
        private final String url;
        private final String path;
        private final String branch;
        private final String commitId;
        private final CommitInfo commitInfo;

        public RepositoryInfo(UUID id,
                              String name,
                              String url,
                              String path,
                              String branch,
                              String commitId,
                              CommitInfo commitInfo) {

            this.id = id;
            this.name = name;
            this.url = url;
            this.path = path;
            this.branch = branch;
            this.commitId = commitId;
            this.commitInfo = commitInfo;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getPath() {
            return path;
        }

        public String getBranch() {
            return branch;
        }

        public String getCommitId() {
            return commitId;
        }

        public CommitInfo getCommitInfo() {
            return commitInfo;
        }

        @Override
        public String toString() {
            return "RepositoryInfo{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", url='" + url + '\'' +
                    ", path='" + path + '\'' +
                    ", branch='" + branch + '\'' +
                    ", commitId='" + commitId + '\'' +
                    ", commitInfo=" + commitInfo +
                    '}';
        }
    }

    public static final class CommitInfo implements Serializable {

        private final String id;
        private final String author;
        private final String message;

        public CommitInfo(String id, String author, String message) {
            this.id = id;
            this.author = author;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public String getAuthor() {
            return author;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "CommitInfo{" +
                    "id='" + id + '\'' +
                    ", author='" + author + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
