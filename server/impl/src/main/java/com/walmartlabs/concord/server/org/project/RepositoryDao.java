package com.walmartlabs.concord.server.org.project;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class RepositoryDao extends AbstractDao {

    @Inject
    public RepositoryDao(Configuration cfg) {
        super(cfg);
    }

    public UUID getId(UUID projectId, String repoName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(REPOSITORIES.REPO_ID)
                    .from(REPOSITORIES)
                    .where(REPOSITORIES.PROJECT_ID.eq(projectId)
                            .and(REPOSITORIES.REPO_NAME.eq(repoName)))
                    .fetchOne(REPOSITORIES.REPO_ID);
        }
    }
    
    public RepositoryEntry get(UUID projectId, UUID repoId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectRepositoryEntry(tx)
                    .where(REPOSITORIES.PROJECT_ID.eq(projectId)
                            .and(REPOSITORIES.REPO_ID.eq(repoId)))
                    .fetchOne(RepositoryDao::toEntry);
        }
    }

    public UUID insert(UUID projectId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId) {
        return txResult(tx -> insert(tx, projectId, repositoryName, url, branch, commitId, path, secretId));
    }

    public UUID insert(DSLContext tx, UUID projectId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId) {
        return tx.insertInto(REPOSITORIES)
                .columns(REPOSITORIES.PROJECT_ID, REPOSITORIES.REPO_NAME,
                        REPOSITORIES.REPO_URL, REPOSITORIES.REPO_BRANCH, REPOSITORIES.REPO_COMMIT_ID,
                        REPOSITORIES.REPO_PATH, REPOSITORIES.SECRET_ID)
                .values(projectId, repositoryName, url, branch, commitId, path, secretId)
                .returning(REPOSITORIES.REPO_ID)
                .fetchOne()
                .getRepoId();
    }

    public void update(DSLContext tx, UUID repoId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId) {
        tx.update(REPOSITORIES)
                .set(REPOSITORIES.REPO_NAME, repositoryName)
                .set(REPOSITORIES.REPO_URL, url)
                .set(REPOSITORIES.SECRET_ID, secretId)
                .set(REPOSITORIES.REPO_BRANCH, branch)
                .set(REPOSITORIES.REPO_COMMIT_ID, commitId)
                .set(REPOSITORIES.REPO_PATH, path)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .execute();
    }

    public void delete(DSLContext tx, UUID repoId) {
        tx.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .execute();
    }

    public void deleteAll(DSLContext tx, UUID projectId) {
        tx.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId))
                .execute();
    }

    public List<RepositoryEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectRepositoryEntry(tx)
                    .fetch(RepositoryDao::toEntry);
        }
    }

    public List<RepositoryEntry> list(UUID projectId) {
        return list(projectId, null, false);
    }

    public List<RepositoryEntry> list(UUID projectId, Field<?> sortField, boolean asc) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<Record8<UUID, UUID, String, String, String, String, String, String>> query = selectRepositoryEntry(tx)
                    .where(REPOSITORIES.PROJECT_ID.eq(projectId));

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(RepositoryDao::toEntry);
        }
    }

    private static SelectJoinStep<Record8<UUID, UUID, String, String, String, String, String, String>> selectRepositoryEntry(DSLContext tx) {
        return tx.select(REPOSITORIES.REPO_ID,
                REPOSITORIES.PROJECT_ID,
                REPOSITORIES.REPO_NAME,
                REPOSITORIES.REPO_URL,
                REPOSITORIES.REPO_BRANCH,
                REPOSITORIES.REPO_COMMIT_ID,
                REPOSITORIES.REPO_PATH,
                SECRETS.SECRET_NAME)
                .from(REPOSITORIES)
                .leftOuterJoin(SECRETS).on(SECRETS.SECRET_ID.eq(REPOSITORIES.SECRET_ID));
    }

    private static RepositoryEntry toEntry(Record8<UUID, UUID, String, String, String, String, String, String> r) {
        return new RepositoryEntry(r.get(REPOSITORIES.REPO_ID),
                r.get(REPOSITORIES.PROJECT_ID),
                r.get(REPOSITORIES.REPO_NAME),
                r.get(REPOSITORIES.REPO_URL),
                r.get(REPOSITORIES.REPO_BRANCH),
                r.get(REPOSITORIES.REPO_COMMIT_ID),
                r.get(REPOSITORIES.REPO_PATH),
                r.get(SECRETS.SECRET_NAME));
    }
}
