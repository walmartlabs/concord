package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.api.project.ProjectVisibility;
import com.walmartlabs.concord.server.api.project.RepositoryEntry;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.records.ProjectsRecord;
import com.walmartlabs.concord.server.team.TeamManager;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static org.jooq.impl.DSL.*;

@Named
public class ProjectDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(ProjectDao.class);

    private final ObjectMapper objectMapper;

    @Inject
    public ProjectDao(Configuration cfg) {
        super(cfg);
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

    public UUID getId(UUID teamId, String projectName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.PROJECT_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.TEAM_ID.eq(teamId)
                            .and(PROJECTS.PROJECT_NAME.eq(projectName)))
                    .fetchOne(PROJECTS.PROJECT_ID);
        }
    }

    public UUID getTeamId(UUID projectId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.TEAM_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .fetchOne(PROJECTS.TEAM_ID);
        }
    }

    public ProjectEntry get(UUID projectId) {
        Projects p = PROJECTS.as("p");
        Field<String> cfgField = p.PROJECT_CFG.cast(String.class);
        Field<String> teamNameField = select(TEAMS.TEAM_NAME).from(TEAMS).where(TEAMS.TEAM_ID.eq(p.TEAM_ID)).asField();

        try (DSLContext tx = DSL.using(cfg)) {
            Record7<UUID, String, String, UUID, String, String, String> r = tx.select(
                    p.PROJECT_ID,
                    p.PROJECT_NAME,
                    p.DESCRIPTION,
                    p.TEAM_ID,
                    teamNameField,
                    cfgField,
                    p.VISIBILITY)
                    .from(p)
                    .where(p.PROJECT_ID.eq(projectId))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            Result<Record7<UUID, String, String, String, String, String, String>> repos = tx.select(
                    REPOSITORIES.REPO_ID,
                    REPOSITORIES.REPO_NAME,
                    REPOSITORIES.REPO_URL,
                    REPOSITORIES.REPO_BRANCH,
                    REPOSITORIES.REPO_COMMIT_ID,
                    REPOSITORIES.REPO_PATH,
                    SECRETS.SECRET_NAME)
                    .from(REPOSITORIES)
                    .leftOuterJoin(SECRETS).on(SECRETS.SECRET_ID.eq(REPOSITORIES.SECRET_ID))
                    .where(REPOSITORIES.PROJECT_ID.eq(projectId))
                    .fetch();

            Map<String, RepositoryEntry> m = new HashMap<>();
            for (Record7<UUID, String, String, String, String, String, String> repo : repos) {
                m.put(repo.get(REPOSITORIES.REPO_NAME),
                        new RepositoryEntry(
                                repo.get(REPOSITORIES.REPO_ID),
                                repo.get(REPOSITORIES.REPO_NAME),
                                repo.get(REPOSITORIES.REPO_URL),
                                repo.get(REPOSITORIES.REPO_BRANCH),
                                repo.get(REPOSITORIES.REPO_COMMIT_ID),
                                repo.get(REPOSITORIES.REPO_PATH),
                                repo.get(SECRETS.SECRET_NAME)));
            }

            Map<String, Object> cfg = deserialize(r.get(cfgField));
            return new ProjectEntry(projectId,
                    r.get(p.PROJECT_NAME),
                    r.get(p.DESCRIPTION),
                    r.get(p.TEAM_ID),
                    r.get(teamNameField),
                    m,
                    cfg,
                    ProjectVisibility.valueOf(r.get(p.VISIBILITY)));
        }
    }

    public UUID insert(UUID teamId, String name, String description, Map<String, Object> cfg, ProjectVisibility visibility) {
        return txResult(tx -> insert(tx, teamId, name, description, cfg, visibility));
    }

    public UUID insert(DSLContext tx, UUID teamId, String name, String description, Map<String, Object> cfg, ProjectVisibility visibility) {
        if (teamId == null) {
            teamId = TeamManager.DEFAULT_TEAM_ID;
        }

        if (visibility == null) {
            visibility = ProjectVisibility.PUBLIC;
        }

        return tx.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_NAME, PROJECTS.DESCRIPTION, PROJECTS.TEAM_ID, PROJECTS.PROJECT_CFG, PROJECTS.VISIBILITY)
                .values(value(name), value(description), value(teamId), field("?::jsonb", serialize(cfg)), value(visibility.toString()))
                .returning(PROJECTS.PROJECT_ID)
                .fetchOne()
                .getProjectId();
    }

    public void update(DSLContext tx, UUID id, UUID teamId, String name, String description, Map<String, Object> cfg) {
        UpdateSetFirstStep<ProjectsRecord> q = tx.update(PROJECTS);

        UpdateSetMoreStep<ProjectsRecord> u = null;
        if (name != null) {
            u = q.set(PROJECTS.PROJECT_NAME, name);
        }

        if (description != null) {
            u = q.set(PROJECTS.DESCRIPTION, description);
        }

        if (teamId != null) {
            u = q.set(PROJECTS.TEAM_ID, teamId);
        }

        if (cfg != null) {
            u = q.set(PROJECTS.PROJECT_CFG, field("?::jsonb", String.class, serialize(cfg)));
        }

        if (u == null) {
            log.warn("update ['{}'] -> nothing to do", id);
            return;
        }

        u.where(PROJECTS.PROJECT_ID.eq(id))
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
        tx.deleteFrom(PROJECT_KV_STORE)
                .where(PROJECT_KV_STORE.PROJECT_ID.eq(projectId))
                .execute();

        tx.deleteFrom(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(projectId))
                .execute();
    }

    public List<ProjectEntry> list(UUID currentUserId, Field<?> sortField, boolean asc) {
        Projects p = PROJECTS.as("p");
        sortField = p.field(sortField);

        Field<String> teamNameField = select(TEAMS.TEAM_NAME)
                .from(TEAMS)
                .where(TEAMS.TEAM_ID.eq(p.TEAM_ID))
                .asField();

        Condition filterByTeamMember = exists(selectFrom(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(currentUserId)
                        .and(USER_TEAMS.TEAM_ID.eq(p.TEAM_ID))));

        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record6<UUID, String, String, UUID, String, String>> q = tx.select(
                    p.PROJECT_ID,
                    p.PROJECT_NAME,
                    p.DESCRIPTION,
                    p.TEAM_ID,
                    teamNameField,
                    p.VISIBILITY)
                    .from(p);

            if (currentUserId != null) {
                q.where(or(p.VISIBILITY.eq(ProjectVisibility.PUBLIC.toString()), filterByTeamMember));
            }

            if (sortField != null) {
                q.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return q.fetch(ProjectDao::toEntry);
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

    private static ProjectEntry toEntry(Record6<UUID, String, String, UUID, String, String> r) {
        return new ProjectEntry(r.get(PROJECTS.PROJECT_ID),
                r.get(PROJECTS.PROJECT_NAME),
                r.get(PROJECTS.DESCRIPTION),
                r.get(PROJECTS.TEAM_ID),
                r.get(4, String.class),
                null,
                null,
                ProjectVisibility.valueOf(r.get(PROJECTS.VISIBILITY)));
    }
}
