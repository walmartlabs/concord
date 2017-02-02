package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectRepos.PROJECT_REPOS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.public_.tables.Repositories.REPOSITORIES;

@Named
public class RepositoryDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(RepositoryDao.class);

    @Inject
    public RepositoryDao(Configuration cfg) {
        super(cfg);
    }

    public String findUrl(String projectId, String repositoryName) {
        try (DSLContext create = DSL.using(cfg)) {
            String url = create.select(REPOSITORIES.REPO_URL)
                    .from(REPOSITORIES, PROJECTS, PROJECT_REPOS)
                    .where(REPOSITORIES.REPO_NAME.eq(repositoryName)
                            .and(REPOSITORIES.REPO_ID.eq(PROJECT_REPOS.REPO_ID)
                                    .and(PROJECT_REPOS.PROJECT_ID.eq(PROJECTS.PROJECT_ID)
                                            .and(PROJECTS.PROJECT_ID.eq(projectId)))))
                    .fetchOne(REPOSITORIES.REPO_URL);

            if (url == null) {
                log.info("findUrl ['{}', '{}'] -> not found", projectId, repositoryName);
                return null;
            }

            log.info("findUrl ['{}', '{}'] -> found: {}", projectId, repositoryName, url);
            return url;
        }
    }

    public void insert(String projectId, String id, String name, String url) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.insertInto(REPOSITORIES)
                    .columns(REPOSITORIES.REPO_ID, REPOSITORIES.REPO_NAME, REPOSITORIES.REPO_URL)
                    .values(id, name, url)
                    .execute();

            create.insertInto(PROJECT_REPOS)
                    .columns(PROJECT_REPOS.PROJECT_ID, PROJECT_REPOS.REPO_ID)
                    .values(projectId, id)
                    .execute();
        });
        log.info("insert ['{}', '{}', '{}'] -> done", id, name, url);
    }

    public void deleteAll(DSLContext create, String projectId) {
        create.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.REPO_ID.in(
                        create.select(PROJECT_REPOS.REPO_ID)
                                .from(PROJECT_REPOS)
                                .where(PROJECT_REPOS.PROJECT_ID.eq(projectId))))
                .execute();
    }
}
