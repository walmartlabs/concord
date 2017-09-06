package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class RepositoryDao extends AbstractDao {

    @Inject
    public RepositoryDao(Configuration cfg) {
        super(cfg);
    }

    public boolean exists(String projectName, String repositoryName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(REPOSITORIES)
                    .where(REPOSITORIES.PROJECT_NAME.eq(projectName)
                            .and(REPOSITORIES.REPO_NAME.eq(repositoryName))));
        }
    }

    public RepositoryEntry get(String projectName, String repositoryName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectRepositoryEntry(tx)
                    .where(REPOSITORIES.PROJECT_NAME.eq(projectName)
                            .and(REPOSITORIES.REPO_NAME.eq(repositoryName)))
                    .fetchOne(RepositoryDao::toEntry);
        }
    }

    public void insert(String projectName, String repositoryName, String url, String branch, String commitId, String path, String secretName) {
        tx(tx -> insert(tx, projectName, repositoryName, url, branch, commitId, path, secretName));
    }

    public void insert(DSLContext tx, String projectName, String repositoryName, String url, String branch, String commitId, String path, String secretName) {
        tx.insertInto(REPOSITORIES)
                .columns(REPOSITORIES.PROJECT_NAME, REPOSITORIES.REPO_NAME,
                        REPOSITORIES.REPO_URL, REPOSITORIES.REPO_BRANCH, REPOSITORIES.REPO_COMMIT_ID,
                        REPOSITORIES.REPO_PATH, REPOSITORIES.SECRET_NAME)
                .values(projectName, repositoryName, url, branch, commitId, path, secretName)
                .execute();
    }

    public void update(DSLContext tx, String repositoryName, String url, String branch, String commitId, String path, String secretName) {
        tx.update(REPOSITORIES)
                .set(REPOSITORIES.REPO_URL, url)
                .set(REPOSITORIES.SECRET_NAME, secretName)
                .set(REPOSITORIES.REPO_BRANCH, branch)
                .set(REPOSITORIES.REPO_COMMIT_ID, commitId)
                .set(REPOSITORIES.REPO_PATH, path)
                .where(REPOSITORIES.REPO_NAME.eq(repositoryName))
                .execute();
    }

    public void delete(DSLContext tx, String repositoryName) {
        tx.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.REPO_NAME.eq(repositoryName))
                .execute();
    }

    public void deleteAll(DSLContext tx, String projectName) {
        tx.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.PROJECT_NAME.eq(projectName))
                .execute();
    }

    public List<RepositoryEntry> list(String projectName, Field<?> sortField, boolean asc) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<Record6<String, String, String, String, String, String>> query = selectRepositoryEntry(tx)
                    .where(REPOSITORIES.PROJECT_NAME.eq(projectName));

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(RepositoryDao::toEntry);
        }
    }

    private static SelectJoinStep<Record6<String, String, String, String, String, String>> selectRepositoryEntry(DSLContext tx) {
        return tx.select(REPOSITORIES.REPO_NAME,
                REPOSITORIES.REPO_URL,
                REPOSITORIES.REPO_BRANCH,
                REPOSITORIES.REPO_COMMIT_ID,
                REPOSITORIES.REPO_PATH,
                SECRETS.SECRET_NAME)
                .from(REPOSITORIES)
                .leftOuterJoin(SECRETS).on(SECRETS.SECRET_NAME.eq(REPOSITORIES.SECRET_NAME));
    }

    private static RepositoryEntry toEntry(Record6<String, String, String, String, String, String> r) {
        return new RepositoryEntry(r.get(REPOSITORIES.REPO_NAME),
                r.get(REPOSITORIES.REPO_URL),
                r.get(REPOSITORIES.REPO_BRANCH),
                r.get(REPOSITORIES.REPO_COMMIT_ID),
                r.get(REPOSITORIES.REPO_PATH),
                r.get(REPOSITORIES.SECRET_NAME));
    }
}
