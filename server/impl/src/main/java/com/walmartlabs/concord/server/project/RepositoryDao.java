package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.public_.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.public_.tables.Secrets.SECRETS;

@Named
public class RepositoryDao extends AbstractDao {

    @Inject
    public RepositoryDao(Configuration cfg) {
        super(cfg);
    }

    public boolean exists(String projectName, String repositoryName) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.fetchExists(create.selectFrom(REPOSITORIES)
                    .where(REPOSITORIES.PROJECT_NAME.eq(projectName)
                            .and(REPOSITORIES.REPO_NAME.eq(repositoryName))));
        }
    }

    public RepositoryEntry get(String projectName, String repositoryName) {
        try (DSLContext create = DSL.using(cfg)) {
            return selectRepositoryEntry(create)
                    .where(REPOSITORIES.PROJECT_NAME.eq(projectName)
                            .and(REPOSITORIES.REPO_NAME.eq(repositoryName)))
                    .fetchOne(RepositoryDao::toEntry);
        }
    }

    public void insert(String projectName, String repositoryName, String url, String branch, String secretName) {
        tx(tx -> {
            insert(tx, projectName, repositoryName, url, branch, secretName);
        });
    }

    public void insert(DSLContext create, String projectName, String repositoryName, String url, String branch, String secretName) {
        create.insertInto(REPOSITORIES)
                .columns(REPOSITORIES.PROJECT_NAME, REPOSITORIES.REPO_NAME,
                        REPOSITORIES.REPO_URL, REPOSITORIES.REPO_BRANCH, REPOSITORIES.SECRET_NAME)
                .values(projectName, repositoryName, url, branch, secretName)
                .execute();
    }

    public void update(DSLContext create, String repositoryName, String url, String branch, String secretName) {
        create.update(REPOSITORIES)
                .set(REPOSITORIES.REPO_URL, url)
                .set(REPOSITORIES.SECRET_NAME, secretName)
                .set(REPOSITORIES.REPO_BRANCH, branch)
                .where(REPOSITORIES.REPO_NAME.eq(repositoryName))
                .execute();
    }

    public void delete(DSLContext create, String repositoryName) {
        create.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.REPO_NAME.eq(repositoryName))
                .execute();
    }

    public void deleteAll(DSLContext create, String projectName) {
        create.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.PROJECT_NAME.eq(projectName))
                .execute();
    }

    public List<RepositoryEntry> list(String projectName, Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectConditionStep<Record4<String, String, String, String>> query = selectRepositoryEntry(create)
                    .where(REPOSITORIES.PROJECT_NAME.eq(projectName));

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(RepositoryDao::toEntry);
        }
    }

    private static SelectJoinStep<Record4<String, String, String, String>> selectRepositoryEntry(DSLContext create) {
        return create.select(REPOSITORIES.REPO_NAME,
                REPOSITORIES.REPO_URL,
                REPOSITORIES.REPO_BRANCH,
                SECRETS.SECRET_NAME)
                .from(REPOSITORIES)
                .leftOuterJoin(SECRETS).on(SECRETS.SECRET_NAME.eq(REPOSITORIES.SECRET_NAME));
    }

    private static RepositoryEntry toEntry(Record4<String, String, String, String> r) {
        return new RepositoryEntry(r.get(REPOSITORIES.REPO_NAME),
                r.get(REPOSITORIES.REPO_URL),
                r.get(REPOSITORIES.REPO_BRANCH),
                r.get(REPOSITORIES.SECRET_NAME));
    }
}
