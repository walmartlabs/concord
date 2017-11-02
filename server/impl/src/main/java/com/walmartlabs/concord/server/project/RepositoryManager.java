package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.sdk.Secret;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;

public interface RepositoryManager {

    String DEFAULT_BRANCH = "master";

    void testConnection(String uri, String branch, String commitId, String path, Secret secret);

    <T> T withLock(UUID projectId, String repoName, Callable<T> f);

    Path fetchByCommit(UUID projectId, String repoName, String uri, String commitId, String path, Secret secret);

    Path getRepoPath(UUID projectId, String repoName, String branch, String path);

    Path fetch(UUID projectId, String repoName, String uri, String branchName, String path, Secret secret);
}