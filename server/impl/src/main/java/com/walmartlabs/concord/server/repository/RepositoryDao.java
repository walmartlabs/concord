package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.repository.RepositoryEntry;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

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

    public String getName(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(REPOSITORIES.REPO_NAME)
                    .from(REPOSITORIES)
                    .where(REPOSITORIES.REPO_ID.eq(id))
                    .fetchOne(REPOSITORIES.REPO_NAME);
        }
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

    public void update(String id, String url) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            int i = create.update(REPOSITORIES)
                    .set(REPOSITORIES.REPO_URL, url)
                    .where(REPOSITORIES.REPO_ID.eq(id))
                    .execute();

            if (i != 1) {
                log.error("update ['{}', '{}'] -> invalid number of rows updated: {}", id, url, i);
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }
        });
        log.info("update ['{}', '{}'] -> done", id, url);
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.deleteFrom(REPOSITORIES)
                    .where(REPOSITORIES.REPO_ID.eq(id))
                    .execute();
        });
        log.info("delete ['{}'] -> done", id);
    }

    public List<RepositoryEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record3<String, String, String>> query = create
                    .select(REPOSITORIES.REPO_ID, REPOSITORIES.REPO_NAME, REPOSITORIES.REPO_URL)
                    .from(REPOSITORIES);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            List<RepositoryEntry> result = query.fetch(r ->
                    new RepositoryEntry(r.get(REPOSITORIES.REPO_ID),
                            r.get(REPOSITORIES.REPO_NAME),
                            r.get(REPOSITORIES.REPO_URL)));
            log.info("list [{}, {}] -> got {} result(s)", result.size());
            return result;
        }
    }

    public boolean exists(String repositoryName) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(REPOSITORIES)
                    .where(REPOSITORIES.REPO_NAME.eq(repositoryName)));

            return cnt > 0;
        }
    }
}
