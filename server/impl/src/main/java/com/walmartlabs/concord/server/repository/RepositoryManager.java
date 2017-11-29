package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;

public interface RepositoryManager {

    String DEFAULT_BRANCH = "master";

    void testConnection(UUID orgId, String uri, String branch, String commitId, String path, String secretName);

    Path fetch(UUID projectId, RepositoryEntry repository);

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