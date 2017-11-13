package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.server.api.project.RepositoryEntry;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;

public interface RepositoryManager {

    String DEFAULT_BRANCH = "master";

    void testConnection(UUID teamId, String uri, String branch, String commitId, String path, String secretName);

    Path fetch(UUID projectId, RepositoryEntry repository);

    <T> T withLock(UUID projectId, String repoName, Callable<T> f);

    Path getRepoPath(UUID projectId, RepositoryEntry repository);
}