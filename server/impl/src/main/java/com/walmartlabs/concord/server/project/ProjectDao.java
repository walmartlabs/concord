package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
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

    public String getName(UUID projectId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.PROJECT_NAME)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .fetchOne(PROJECTS.PROJECT_NAME);
        }
    }

    public UUID getId(String projectName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.PROJECT_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(projectName))
                    .fetchOne(PROJECTS.PROJECT_ID);
        }
    }

    public ProjectEntry getByName(String name) {
        Projects p = PROJECTS.as("p");
        Field<String> cfgField = p.PROJECT_CFG.cast(String.class);

        try (DSLContext tx = DSL.using(cfg)) {
            Record4<UUID, String, String, String> r = tx.select(p.PROJECT_ID, p.PROJECT_NAME, p.DESCRIPTION, cfgField)
                    .from(p)
                    .where(p.PROJECT_NAME.eq(name))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            UUID projectId = r.get(p.PROJECT_ID);

            Result<Record6<String, String, String, String, String, String>> repos = tx.select(
                    REPOSITORIES.REPO_NAME,
                    REPOSITORIES.REPO_URL,
                    REPOSITORIES.REPO_BRANCH,
                    REPOSITORIES.REPO_COMMIT_ID,
                    REPOSITORIES.REPO_PATH,
                    SECRETS.SECRET_NAME)
                    .from(REPOSITORIES)
                    .leftOuterJoin(SECRETS).on(SECRETS.SECRET_ID.eq(REPOSITORIES.REPO_ID))
                    .where(REPOSITORIES.PROJECT_ID.eq(projectId))
                    .fetch();

            Map<String, UpdateRepositoryRequest> m = new HashMap<>();
            for (Record6<String, String, String, String, String, String> repo : repos) {
                m.put(repo.get(REPOSITORIES.REPO_NAME),
                        new UpdateRepositoryRequest(
                                repo.get(REPOSITORIES.REPO_URL),
                                repo.get(REPOSITORIES.REPO_BRANCH),
                                repo.get(REPOSITORIES.REPO_COMMIT_ID),
                                repo.get(REPOSITORIES.REPO_PATH),
                                repo.get(SECRETS.SECRET_NAME)));
            }

            Map<String, Object> cfg = deserialize(r.get(cfgField));
            return new ProjectEntry(projectId, r.get(p.PROJECT_NAME), r.get(p.DESCRIPTION), m, cfg);
        }
    }

    public UUID insert(String name, String description, Map<String, Object> cfg) {
        return txResult(tx -> insert(tx, name, description, cfg));
    }

    public UUID insert(DSLContext tx, String name, String description, Map<String, Object> cfg) {
        return tx.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION, PROJECTS.PROJECT_CFG)
                .values(value(name), value(description), field("?::jsonb", serialize(cfg)))
                .returning(PROJECTS.PROJECT_ID)
                .fetchOne()
                .getProjectId();
    }

    public void update(DSLContext tx, UUID id, String name, String description, Map<String, Object> cfg) {
        tx.update(PROJECTS)
                .set(PROJECTS.PROJECT_NAME, name)
                .set(PROJECTS.DESCRIPTION, description)
                .set(PROJECTS.PROJECT_CFG, field("?::jsonb", String.class, serialize(cfg)))
                .where(PROJECTS.PROJECT_ID.eq(id))
                .execute();
    }

    public void update(DSLContext tx, UUID id, Map<String, Object> cfg) {
        tx.update(PROJECTS)
                .set(PROJECTS.PROJECT_CFG, field("?::jsonb", String.class, serialize(cfg)))
                .where(PROJECTS.PROJECT_ID.eq(id))
                .execute();
    }

    public void delete(UUID projectId) {
        tx(tx -> delete(tx, projectId));
    }

    public void delete(DSLContext tx, UUID projectId) {
        permissionCleaner.onProjectRemoval(tx, getName(projectId));

        tx.deleteFrom(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_ID.eq(projectId))
                .execute();

        tx.deleteFrom(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(projectId))
                .execute();
    }

    public List<ProjectEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record3<UUID, String, String>> query = selectCreateProjectRequest(tx);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(ProjectDao::toEntry);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfiguration(UUID projectId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.PROJECT_CFG.cast(String.class))
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .fetchOne(e -> deserialize(e.value1()));
        }
    }

    public Object getConfigurationValue(UUID projectId, String... path) {
        Map<String, Object> cfg = getConfiguration(projectId);
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

    private static ProjectEntry toEntry(Record3<UUID, String, String> r) {
        return new ProjectEntry(r.get(PROJECTS.PROJECT_ID), r.get(PROJECTS.PROJECT_NAME),
                r.get(PROJECTS.DESCRIPTION), null, null);
    }

    private static SelectJoinStep<Record3<UUID, String, String>> selectCreateProjectRequest(DSLContext tx) {
        return tx.select(PROJECTS.PROJECT_ID, PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION)
                .from(PROJECTS);
    }
}
