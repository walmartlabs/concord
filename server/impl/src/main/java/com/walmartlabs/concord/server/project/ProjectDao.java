package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.jooq.tables.records.RepositoriesRecord;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.value;

@Named
public class ProjectDao extends AbstractDao {

    private final UserPermissionCleaner permissionCleaner;
    private final ObjectMapper objectMapper;

    @Inject
    public ProjectDao(Configuration cfg, UserPermissionCleaner permissionCleaner) {
        super(cfg);
        this.permissionCleaner = permissionCleaner;
        this.objectMapper = new ObjectMapper();
    }

    public ProjectEntry get(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            Record3<String, String, String> r = tx.select(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION, PROJECTS.PROJECT_CFG.cast(String.class))
                    .from(PROJECTS)
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
                        repo.getRepoBranch(), repo.getRepoCommitId(),
                        repo.getRepoPath(), repo.getSecretName()));
            }

            Map<String, Object> cfg = deserialize(r.value3());
            return new ProjectEntry(r.value1(), r.value2(), m, cfg);
        }
    }

    public void insert(String name, String description, Map<String, Object> cfg) {
        tx(tx -> insert(tx, name, description, cfg));
    }

    public void insert(DSLContext tx, String name, String description, Map<String, Object> cfg) {
        tx.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION, PROJECTS.PROJECT_CFG)
                .values(value(name), value(description), field("?::jsonb", serialize(cfg)))
                .execute();
    }

    public void update(DSLContext tx, String name, String description, Map<String, Object> cfg) {
        tx.update(PROJECTS)
                .set(PROJECTS.DESCRIPTION, description)
                .set(PROJECTS.PROJECT_CFG, field("?::jsonb", String.class, serialize(cfg)))
                .where(PROJECTS.PROJECT_NAME.eq(name))
                .execute();
    }

    public void update(DSLContext tx, String name, Map<String, Object> cfg) {
        tx.update(PROJECTS)
                .set(PROJECTS.PROJECT_CFG, field("?::jsonb", String.class, serialize(cfg)))
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfiguration(String projectName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.PROJECT_CFG.cast(String.class))
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(projectName))
                    .fetchOne(e -> deserialize(e.value1()));
        }
    }

    public Object getConfigurationValue(String projectName, String... path) {
        Map<String, Object> cfg = getConfiguration(projectName);
        return ConfigurationUtils.get(cfg, path);
    }

    private String serialize(Map<String, Object> m) {
        if (m == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String ab) {
        if (ab == null) {
            return null;
        }

        try {
            return objectMapper.readValue(ab, Map.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
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
