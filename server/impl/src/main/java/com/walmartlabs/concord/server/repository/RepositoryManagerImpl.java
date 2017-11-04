package com.walmartlabs.concord.server.repository;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Striped;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import com.walmartlabs.concord.server.project.RepositoryException;
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

    private static final long LOCK_TIMEOUT = 30000;

    private final Striped<Lock> locks = Striped.lock(32);

    private final RepositoryConfiguration cfg;
    private final RepositoryProvider githubRepositoryProvider;
    private final RepositoryProvider classpathRepositoryProvider;

    public RepositoryManagerImpl(RepositoryConfiguration cfg,
                                 GithubRepositoryProvider githubRepositoryProvider,
                                 ClasspathRepositoryProvider classpathRepositoryProvider) {
        this.cfg = cfg;
        this.githubRepositoryProvider = githubRepositoryProvider;
        this.classpathRepositoryProvider = classpathRepositoryProvider;
    }

    @Override
    public void testConnection(String uri, String branch, String commitId, String path, String secretName) {
        Path tmpDir = null;

        try {
            tmpDir = Files.createTempDirectory("repository");
            getProvider(uri).fetch(new RepositoryEntry(null, null, uri, branch, commitId, path, secretName), tmpDir);
            repoPath(tmpDir, path);
        } catch (IOException e) {
            log.error("testConnection ['{}', '{}', '{}', '{}', '{}'] -> error", uri, branch, commitId, path, secretName, e);
            throw new RepositoryException("test connection error", e);
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
        Path localPath = localPath(projectId, repository);
        RepositoryProvider provider = getProvider(repository.getUrl());
        return withLock(projectId, repository.getName(), () -> {
            provider.fetch(repository, localPath);
            return repoPath(localPath, repository.getPath());
        });
    }

    @Override
    public <T> T withLock(UUID projectId, String repoName, Callable<T> f) {
        Lock l = locks.get(projectId + "/" + repoName);
        try {
            l.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            return f.call();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            l.unlock();
        }
    }

    @Override
    public Path getRepoPath(UUID projectId, RepositoryEntry repository) {
        Path localPath = localPath(projectId, repository);
        return repoPath(localPath, repository.getPath());
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

        return cfg.getRepoCacheDir()
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
            throw new RepositoryException("Invalid repository path: " + p);
        }

        return repoDir;
    }
}
