package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.sdk.Secret;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Callable;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.project.RepositoryMetaManager.RepositoryMeta;

public class CachedRepositoryManager implements RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(CachedRepositoryManager.class);

    private final RepositoryMetaManager repositoryMetaManager;

    private final RepositoryManager delegate;

    private final RepositoryCacheDao repositoryDao;

    public CachedRepositoryManager(RepositoryMetaManager repositoryMetaManager,
                                   RepositoryManager delegate,
                                   RepositoryCacheDao repositoryDao) {
        this.repositoryMetaManager = repositoryMetaManager;
        this.delegate = delegate;
        this.repositoryDao = repositoryDao;
    }

    @Override
    public void testConnection(String uri, String branch, String commitId, String path, Secret secret) {
        delegate.testConnection(uri, branch, commitId, path, secret);
    }

    @Override
    public <T> T withLock(UUID projectId, String repoName, Callable<T> f) {
        return delegate.withLock(projectId, repoName, f);
    }

    @Override
    public Path fetchByCommit(UUID projectId, String repoName, String uri, String commitId, String path, Secret secret) {
        return fetch(projectId, repoName, commitId, path, () -> delegate.fetchByCommit(projectId, repoName, uri, commitId, path, secret));
    }

    @Override
    public Path fetch(UUID projectId, String repoName, String uri, String branchName, String path, Secret secret) {
        return fetch(projectId, repoName, branchName, path, () -> delegate.fetch(projectId, repoName, uri, branchName, path, secret));
    }

    @Override
    public Path getRepoPath(UUID projectId, String repoName, String branch, String path) {
        return delegate.getRepoPath(projectId, repoName, branch, path);
    }

    private Path fetch(UUID projectId, String repoName, String branchOrCommit, String path, Callable<Path> f) {
        return withLock(projectId, repoName, () -> {
            Date lastPushDate = repositoryDao.getLastPushDate(projectId, repoName);
            RepositoryMeta rm = repositoryMetaManager.readMeta(projectId, repoName, branchOrCommit);
            if (needUpdate(rm, lastPushDate)) {
                Path p = f.call();
                repositoryMetaManager.writeMeta(projectId, repoName, branchOrCommit, new RepositoryMeta(lastPushDate));
                log.info("fetch ['{}', '{}'] -> updated", projectId, repoName);
                return p;
            } else {
                log.info("fetch ['{}', '{}'] -> from cache", projectId, repoName);
                return delegate.getRepoPath(projectId, repoName, branchOrCommit, path);
            }
        });
    }

    private boolean needUpdate(RepositoryMeta rm, Date lastPushDate) {
        return rm == null || rm.getPushDate().before(lastPushDate);
    }

    @Named
    public static class RepositoryCacheDao extends AbstractDao {

        @Inject
        public RepositoryCacheDao(Configuration cfg) {
            super(cfg);
        }

        public Date getLastPushDate(UUID projectId, String repoName) {
            try (DSLContext tx = DSL.using(cfg)) {
                return tx.select(REPOSITORIES.PUSH_EVENT_DATE)
                        .from(REPOSITORIES)
                        .where(REPOSITORIES.PROJECT_ID.eq(projectId)
                                .and(REPOSITORIES.REPO_NAME.eq(repoName)))
                        .fetchOne(REPOSITORIES.PUSH_EVENT_DATE);
            }
        }

        public boolean updateLastPushDate(UUID repoId, Date pushDate) {
            return txResult(tx -> tx.update(REPOSITORIES)
                    .set(REPOSITORIES.PUSH_EVENT_DATE, new Timestamp(pushDate.getTime()))
                    .where(REPOSITORIES.REPO_ID.eq(repoId))
                    .execute() == 1);
        }
    }
}