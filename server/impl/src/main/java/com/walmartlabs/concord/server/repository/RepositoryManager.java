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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.repository.*;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.cfg.GitConfiguration;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.secret.SecretManager.AccessScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.walmartlabs.concord.process.loader.ProjectLoaderUtils.isConcordFileExists;

public class RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryManager.class);

    private final RepositoryProviders providers;
    private final ProjectDao projectDao;
    private final SecretManager secretManager;
    private final RepositoryCache repositoryCache;
    private final RepositoryConfiguration repoCfg;
    private final GitConfiguration gitCfg;

    @Inject
    public RepositoryManager(ObjectMapper objectMapper,
                             GitConfiguration gitCfg,
                             RepositoryConfiguration repoCfg,
                             ProjectDao projectDao,
                             SecretManager secretManager,
                             DependencyManager dependencyManager) throws IOException {

        GitClientConfiguration gitCliCfg = GitClientConfiguration.builder()
                .oauthToken(gitCfg.getOauthToken())
                .systemGitAuthProviders(gitCfg.getSystemGitAuthProviders())
                .authorizedGitHosts(gitCfg.getAuthorizedGitHosts())
                .defaultOperationTimeout(gitCfg.getDefaultOperationTimeout())
                .fetchTimeout(gitCfg.getFetchTimeout())
                .httpLowSpeedLimit(gitCfg.getHttpLowSpeedLimit())
                .httpLowSpeedTime(gitCfg.getHttpLowSpeedTime())
                .sshTimeout(gitCfg.getSshTimeout())
                .sshTimeoutRetryCount(gitCfg.getSshTimeoutRetryCount())
                .build();

        List<RepositoryProvider> providers = Arrays.asList(new ClasspathRepositoryProvider(), new MavenRepositoryProvider(dependencyManager), new GitCliRepositoryProvider(gitCliCfg));

        this.gitCfg = gitCfg;
        this.providers = new RepositoryProviders(providers);
        this.secretManager = secretManager;
        this.projectDao = projectDao;
        this.repoCfg = repoCfg;

        this.repositoryCache = new RepositoryCache(repoCfg.getCacheDir(),
                repoCfg.getCacheInfoDir(),
                repoCfg.getLockTimeout(),
                repoCfg.getMaxAge(),
                repoCfg.getLockCount(),
                objectMapper);
    }

    public void testConnection(UUID orgId, UUID projectId, String uri, String branch, String commitId, String path, String secretName) {
        Path tmpDir = null;
        try {
            tmpDir = PathUtils.createTempDir("repository");

            Secret secret = getSecret(orgId, projectId, secretName);

            Repository repo = providers.fetch(
                    FetchRequest.builder()
                            .url(uri)
                            .shallow(gitCfg.isShallowClone())
                            .version(FetchRequest.Version.commitWithBranch(commitId, branch))
                            .secret(secret)
                            .destination(tmpDir)
                            .build(), path);

            if (repoCfg.isConcordFileValidationEnabled()) {
                if (!isConcordFileExists(repo.path())) {
                    throw new InvalidRepositoryPathException("Invalid repository path: `concord.yml` or `.concord.yml` is missing!");
                }
            }
        } catch (IOException e) {
            log.error("testConnection ['{}', '{}', '{}', '{}', '{}'] -> error", uri, branch, commitId, path, secretName, e);
            throw new RepositoryException("Test connection error", e);
        } finally {
            if (tmpDir != null) {
                try {
                    PathUtils.deleteRecursively(tmpDir);
                } catch (IOException e) {
                    log.warn("testConnection -> cleanup error: {}", e.getMessage());
                }
            }
        }
    }

    public Repository fetch(String url, FetchRequest.Version version, String path, Secret secret, boolean withCommitInfo) {
        Path dest = repositoryCache.getPath(url);
        return providers.fetch(
                FetchRequest.builder()
                        .url(url)
                        .shallow(gitCfg.isShallowClone())
                        .checkAlreadyFetched(gitCfg.isCheckAlreadyFetched())
                        .version(version)
                        .secret(secret)
                        .destination(dest)
                        .withCommitInfo(withCommitInfo)
                        .build(), path);
    }

    public Repository fetch(String url, String branch, String commitId, String path, Secret secret, boolean withCommitInfo) {
        String fetchedCommitId = commitId;
        long start = System.currentTimeMillis();

        Path dest = repositoryCache.getPath(url);
        try {
            Repository result = providers.fetch(
                    FetchRequest.builder()
                            .url(url)
                            .shallow(gitCfg.isShallowClone())
                            .checkAlreadyFetched(gitCfg.isCheckAlreadyFetched())
                            .version(FetchRequest.Version.commitWithBranch(commitId, branch))
                            .secret(secret)
                            .destination(dest)
                            .withCommitInfo(withCommitInfo)
                            .build(), path);
            fetchedCommitId = result.fetchResult() != null ? Objects.requireNonNull(result.fetchResult()).head() : null;
            return result;
        } finally {
            log.info("fetch ['{}', '{}', '{}', '{}'] -> current commitId {}, done in {}ms",
                    url, branch, commitId, path, fetchedCommitId, (System.currentTimeMillis() - start));
        }
    }

    public Repository fetch(UUID projectId, RepositoryEntry repository) {
        return fetch(projectId, repository, false);
    }

    public Repository fetch(UUID projectId, RepositoryEntry repository, boolean withCommitInfo) {
        UUID orgId = getOrgId(projectId);
        Secret secret = getSecret(orgId, projectId, repository.getSecretName());
        return fetch(repository.getUrl(), repository.getBranch(), repository.getCommitId(), repository.getPath(), secret, withCommitInfo);
    }

    public <T> T withLock(String repoUrl, Callable<T> f) {
        long start = System.currentTimeMillis();
        try {
            return repositoryCache.withLock(repoUrl, f);
        } finally {
            log.info("withLock ['{}'] -> done in {}ms", repoUrl, (System.currentTimeMillis() - start));
        }
    }

    public void cleanup() {
        repositoryCache.cleanup();
    }

    public long cleanupInterval() {
        return repositoryCache.cleanupInterval();
    }

    private UUID getOrgId(UUID projectId) {
        UUID orgId = projectDao.getOrgId(projectId);

        if (orgId == null) {
            log.warn("getOrgId ['{}'] -> can't determine the project's organization ID", projectId);
            return OrganizationManager.DEFAULT_ORG_ID;
        }

        return orgId;
    }

    private Secret getSecret(UUID orgId, UUID projectId, String secretName) {
        if (secretName == null) {
            return null;
        }

        SecretManager.DecryptedSecret s = secretManager.getSecret(AccessScope.project(projectId), orgId, secretName, null, null);
        if (s == null) {
            throw new RepositoryException("Secret not found: " + secretName);
        }

        return s.getSecret();
    }
}
