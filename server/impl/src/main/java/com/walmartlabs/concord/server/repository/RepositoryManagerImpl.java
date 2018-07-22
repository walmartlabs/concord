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

import com.google.common.util.concurrent.Striped;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.ProjectLoader;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.project.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

public class RepositoryManagerImpl implements RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryManagerImpl.class);

    private final Striped<Lock> locks = Striped.lock(32);

    private final RepositoryConfiguration cfg;
    private final RepositoryProvider githubRepositoryProvider;
    private final RepositoryProvider classpathRepositoryProvider;
    private final ProjectDao projectDao;

    public RepositoryManagerImpl(RepositoryConfiguration cfg,
                                 RepositoryProvider githubRepositoryProvider,
                                 RepositoryProvider classpathRepositoryProvider,
                                 ProjectDao projectDao) {

        this.cfg = cfg;
        this.githubRepositoryProvider = githubRepositoryProvider;
        this.classpathRepositoryProvider = classpathRepositoryProvider;
        this.projectDao = projectDao;
    }

    @Override
    public void testConnection(UUID orgId, String uri, String branch, String commitId, String path, String secretName) {
        Path tmpDir = null;
        try {
            tmpDir = IOUtils.createTempDir("repository");

            RepositoryEntry repo = new RepositoryEntry(null, null, null, uri, branch, commitId, path, null, secretName, false, null);
            getProvider(uri).fetch(orgId, repo, tmpDir);
            Path repoPath = repoPath(tmpDir, path);

            if (cfg.isConcordFileValidationEnabled()) {
                if (!isConcordFileExists(repoPath)) {
                    throw new InvalidRepositoryPathException("Invalid repository path: `concord.yml` or `.concord.yml` is missing!");
                }
            }
        } catch (IOException e) {
            log.error("testConnection ['{}', '{}', '{}', '{}', '{}'] -> error", uri, branch, commitId, path, secretName, e);
            throw new RepositoryException("Test connection error", e);
        } finally {
            if (tmpDir != null) {
                try {
                    IOUtils.deleteRecursively(tmpDir);
                } catch (IOException e) {
                    log.warn("testConnection -> cleanup error: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public Path fetch(UUID projectId, RepositoryEntry repository) {
        UUID orgId = getOrgId(projectId);

        Path localPath = localPath(projectId, repository);
        RepositoryProvider provider = getProvider(repository.getUrl());

        return withLock(projectId, repository.getName(), () -> {
            try {
                provider.fetch(orgId, repository, localPath);
            } catch (RepositoryException e) {
                log.warn("fetch ['{}', '{}'] -> error: {}, retrying...", projectId, repository, e.getMessage());

                try {
                    IOUtils.deleteRecursively(localPath);
                } catch (IOException ee) {
                    log.warn("fetch ['{}', '{}'] -> cleanup error: {}", projectId, repository, ee.getMessage());
                }

                // retry
                provider.fetch(orgId, repository, localPath);
            }
            return repoPath(localPath, repository.getPath());
        });
    }

    @Override
    public <T> T withLock(UUID projectId, String repoName, Callable<T> f) {
        Lock l = locks.get(projectId + "/" + repoName);
        try {
            l.tryLock(cfg.getLockTimeout(), TimeUnit.MILLISECONDS);
            return f.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            l.unlock();
        }
    }

    @Override
    public Path getRepoPath(UUID projectId, RepositoryEntry repository) {
        Path localPath = localPath(projectId, repository);
        return repoPath(localPath, repository.getPath());
    }

    @Override
    public RepositoryInfo getInfo(RepositoryEntry repository, Path path) {
        RepositoryProvider provider = getProvider(repository.getUrl());
        return withLock(repository.getProjectId(), repository.getName(), () -> provider.getInfo(path));
    }

    private boolean isConcordFileExists(Path repoPath) {
        for (String projectFileName : ProjectLoader.PROJECT_FILE_NAMES) {
            Path projectFile = repoPath.resolve(projectFileName);
            if (Files.exists(projectFile)) {
                return true;
            }
        }

        return false;
    }

    private UUID getOrgId(UUID projectId) {
        UUID orgId = projectDao.getOrgId(projectId);

        if (orgId == null) {
            log.warn("getOrgId ['{}'] -> can't determine the project's organization ID", projectId);
            return OrganizationManager.DEFAULT_ORG_ID;
        }

        return orgId;
    }

    private RepositoryProvider getProvider(String url) {
        if (url.startsWith(ClasspathRepositoryProvider.URL_PREFIX)) {
            return classpathRepositoryProvider;
        } else {
            return githubRepositoryProvider;
        }
    }

    private Path localPath(UUID projectId, RepositoryEntry repository) {
        String branch;
        if (repository.getCommitId() != null) {
            branch = repository.getCommitId();
        } else {
            branch = Optional.ofNullable(repository.getBranch()).orElse(DEFAULT_BRANCH);
        }

        return cfg.getCacheDir()
                .resolve(String.valueOf(projectId))
                .resolve(repository.getName())
                .resolve(branch);
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
