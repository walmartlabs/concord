package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.jooq.tables.records.ProjectsRecord;
import com.walmartlabs.concord.server.jooq.tables.records.RepositoriesRecord;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;

@Named
public class ProjectDao extends AbstractDao {

    private final UserPermissionCleaner permissionCleaner;

    @Inject
    public ProjectDao(Configuration cfg, UserPermissionCleaner permissionCleaner) {
        super(cfg);
        this.permissionCleaner = permissionCleaner;
    }

    public ProjectEntry get(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            ProjectsRecord r = tx.selectFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            Result<RepositoriesRecord> repos = tx.selectFrom(REPOSITORIES)
                    .where(REPOSITORIES.PROJECT_NAME.eq(name))
                    .fetch();

            Map<String, UpdateRepositoryRequest> m = new HashMap<>();
            for (RepositoriesRecord repo : repos) {
                m.put(repo.getRepoName(), new UpdateRepositoryRequest(repo.getRepoUrl(),
                        repo.getRepoBranch(), repo.getRepoCommitId(), repo.getSecretName()));
            }

            return new ProjectEntry(r.getProjectName(), r.getDescription(), m, null);
        }
    }

    public void insert(String name, String description) {
        tx(tx -> insert(tx, name, description));
    }

    public void insert(DSLContext tx, String name, String description) {
        tx.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION)
                .values(name, description)
                .execute();
    }

    public void update(DSLContext tx, String name, String description) {
        tx.update(PROJECTS)
                .set(PROJECTS.DESCRIPTION, description)
                .where(PROJECTS.PROJECT_NAME.eq(name))
                .execute();
    }

    public void delete(String id) {
        tx(tx -> delete(tx, id));
    }

    public void delete(DSLContext tx, String name) {
        permissionCleaner.onProjectRemoval(tx, name);

        tx.deleteFrom(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_NAME.eq(name))
                .execute();

        tx.deleteFrom(PROJECTS)
                .where(PROJECTS.PROJECT_NAME.eq(name))
                .execute();
    }

    public List<ProjectEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record2<String, String>> query = selectCreateProjectRequest(tx);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(ProjectDao::toEntry);
        }
    }

    public boolean exists(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.fetchExists(tx.selectFrom(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(name)));
        }
    }

    private static ProjectEntry toEntry(Record2<String, String> r) {
        return new ProjectEntry(r.value1(), r.value2(), null, null);
    }

    private static SelectJoinStep<Record2<String, String>> selectCreateProjectRequest(DSLContext tx) {
        return tx.select(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION)
                .from(PROJECTS);
    }
}
