package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.records.ProjectsRecord;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;
import static com.walmartlabs.concord.server.jooq.tables.ProjectTeamAccess.PROJECT_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

@Named
public class ProjectDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public ProjectDao(@Named("app") Configuration cfg) {
        super(cfg);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public String getName(UUID projectId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.PROJECT_NAME)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .fetchOne(PROJECTS.PROJECT_NAME);
        }
    }

    public UUID getId(UUID orgId, String projectName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.PROJECT_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.ORG_ID.eq(orgId)
                            .and(PROJECTS.PROJECT_NAME.eq(projectName)))
                    .fetchOne(PROJECTS.PROJECT_ID);
        }
    }

    public UUID getOrgId(UUID projectId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.ORG_ID)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .fetchOne(PROJECTS.ORG_ID);
        }
    }

    public Optional<Boolean> isAcceptsRawPayload(UUID projectId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(PROJECTS.ACCEPTS_RAW_PAYLOAD)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .fetchOptional(PROJECTS.ACCEPTS_RAW_PAYLOAD);
        }
    }

    public ProjectEntry get(UUID projectId) {
        Projects p = PROJECTS.as("p");

        Field<String> cfgField = p.PROJECT_CFG.cast(String.class);
        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME).from(ORGANIZATIONS).where(ORGANIZATIONS.ORG_ID.eq(p.ORG_ID)).asField();
        Field<String> ownerUsernameField = select(USERS.USERNAME).from(USERS).where(USERS.USER_ID.eq(p.OWNER_ID)).asField();
        Field<String> metaField = p.META.cast(String.class);

        try (DSLContext tx = DSL.using(cfg)) {
            Record11<UUID, String, String, UUID, String, String, String, UUID, String, Boolean, String> r = tx.select(
                    p.PROJECT_ID,
                    p.PROJECT_NAME,
                    p.DESCRIPTION,
                    p.ORG_ID,
                    orgNameField,
                    cfgField,
                    p.VISIBILITY,
                    p.OWNER_ID,
                    ownerUsernameField,
                    p.ACCEPTS_RAW_PAYLOAD,
                    metaField)
                    .from(p)
                    .where(p.PROJECT_ID.eq(projectId))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            Result<Record10<UUID, UUID, String, String, String, String, String, UUID, String, String>> repos = tx.select(
                    REPOSITORIES.REPO_ID,
                    REPOSITORIES.PROJECT_ID,
                    REPOSITORIES.REPO_NAME,
                    REPOSITORIES.REPO_URL,
                    REPOSITORIES.REPO_BRANCH,
                    REPOSITORIES.REPO_COMMIT_ID,
                    REPOSITORIES.REPO_PATH,
                    SECRETS.SECRET_ID,
                    SECRETS.SECRET_NAME,
                    SECRETS.STORE_TYPE)
                    .from(REPOSITORIES)
                    .leftOuterJoin(SECRETS).on(SECRETS.SECRET_ID.eq(REPOSITORIES.SECRET_ID))
                    .where(REPOSITORIES.PROJECT_ID.eq(projectId))
                    .fetch();

            Map<String, RepositoryEntry> m = new HashMap<>();
            for (Record10<UUID, UUID, String, String, String, String, String, UUID, String, String> repo : repos) {
                m.put(repo.get(REPOSITORIES.REPO_NAME),
                        new RepositoryEntry(
                                repo.get(REPOSITORIES.REPO_ID),
                                repo.get(REPOSITORIES.PROJECT_ID),
                                repo.get(REPOSITORIES.REPO_NAME),
                                repo.get(REPOSITORIES.REPO_URL),
                                repo.get(REPOSITORIES.REPO_BRANCH),
                                repo.get(REPOSITORIES.REPO_COMMIT_ID),
                                repo.get(REPOSITORIES.REPO_PATH),
                                repo.get(SECRETS.SECRET_ID),
                                repo.get(SECRETS.SECRET_NAME),
                                repo.get(SECRETS.STORE_TYPE)));
            }

            Map<String, Object> cfg = deserialize(r.get(cfgField));
            return new ProjectEntry(projectId,
                    r.get(p.PROJECT_NAME),
                    r.get(p.DESCRIPTION),
                    r.get(p.ORG_ID),
                    r.get(orgNameField),
                    m,
                    cfg,
                    ProjectVisibility.valueOf(r.get(p.VISIBILITY)),
                    toOwner(r.get(p.OWNER_ID), r.get(ownerUsernameField)),
                    r.get(p.ACCEPTS_RAW_PAYLOAD),
                    deserialize(r.get(metaField)));
        }
    }

    public UUID insert(UUID orgId, String name, String description, UUID ownerId, Map<String, Object> cfg,
                       ProjectVisibility visibility, boolean acceptsRawPayload, byte[] encryptedKey, Map<String, Object> meta) {

        return txResult(tx -> insert(tx, orgId, name, description, ownerId, cfg, visibility, acceptsRawPayload, encryptedKey, meta));
    }

    public UUID insert(DSLContext tx, UUID orgId, String name, String description, UUID ownerId, Map<String, Object> cfg,
                       ProjectVisibility visibility, boolean acceptsRawPayload, byte[] encryptedKey, Map<String, Object> meta) {

        if (visibility == null) {
            visibility = ProjectVisibility.PUBLIC;
        }

        return tx.insertInto(PROJECTS)
                .columns(PROJECTS.PROJECT_NAME,
                        PROJECTS.DESCRIPTION,
                        PROJECTS.ORG_ID,
                        PROJECTS.PROJECT_CFG,
                        PROJECTS.VISIBILITY,
                        PROJECTS.OWNER_ID,
                        PROJECTS.ACCEPTS_RAW_PAYLOAD,
                        PROJECTS.SECRET_KEY,
                        PROJECTS.META)
                .values(value(name),
                        value(description),
                        value(orgId),
                        field("?::jsonb", serialize(cfg)),
                        value(visibility.toString()),
                        value(ownerId),
                        value(acceptsRawPayload),
                        value(encryptedKey),
                        field("?::jsonb", serialize(meta)))
                .returning(PROJECTS.PROJECT_ID)
                .fetchOne()
                .getProjectId();
    }

    public void update(DSLContext tx, UUID orgId, UUID id, ProjectVisibility visibility,
                       String name, String description, Map<String, Object> cfg, Boolean acceptsRawPayload,
                       Map<String, Object> meta) {
        UpdateSetFirstStep<ProjectsRecord> q = tx.update(PROJECTS);

        if (name != null) {
            q.set(PROJECTS.PROJECT_NAME, name);
        }

        if (description != null) {
            q.set(PROJECTS.DESCRIPTION, description);
        }

        if (cfg != null) {
            q.set(PROJECTS.PROJECT_CFG, field("?::jsonb", String.class, serialize(cfg)));
        }

        if (visibility != null) {
            q.set(PROJECTS.VISIBILITY, visibility.toString());
        }

        if (acceptsRawPayload != null) {
            q.set(PROJECTS.ACCEPTS_RAW_PAYLOAD, acceptsRawPayload);
        }

        if (meta != null) {
            q.set(PROJECTS.META, field("?::jsonb", String.class, serialize(meta)));
        }

        q.set(PROJECTS.ORG_ID, orgId)
                .where(PROJECTS.PROJECT_ID.eq(id))
                .execute();
    }

    public void updateCfg(UUID id, Map<String, Object> cfg) {
        tx(tx -> updateCfg(tx, id, cfg));
    }

    public void updateCfg(DSLContext tx, UUID id, Map<String, Object> cfg) {
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

    public List<ProjectEntry> list(UUID orgId, UUID currentUserId, Field<?> sortField, boolean asc) {
        // TODO simplify

        Projects p = PROJECTS.as("p");
        sortField = p.field(sortField);

        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(p.ORG_ID))
                .asField();

        Field<String> ownerUsernameField = select(USERS.USERNAME)
                .from(USERS)
                .where(USERS.USER_ID.eq(p.OWNER_ID))
                .asField();

        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                .from(TEAMS)
                .where(TEAMS.ORG_ID.eq(orgId));

        Condition filterByTeamMember = exists(selectOne().from(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(currentUserId)
                        .and(USER_TEAMS.TEAM_ID.in(teamIds))));

        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record9<UUID, String, String, UUID, String, String, UUID, String, Boolean>> q = tx.select(
                    p.PROJECT_ID,
                    p.PROJECT_NAME,
                    p.DESCRIPTION,
                    p.ORG_ID,
                    orgNameField,
                    p.VISIBILITY,
                    p.OWNER_ID,
                    ownerUsernameField,
                    p.ACCEPTS_RAW_PAYLOAD)
                    .from(p);

            if (currentUserId != null) {
                q.where(or(p.VISIBILITY.eq(ProjectVisibility.PUBLIC.toString()), filterByTeamMember));
            }

            if (orgId != null) {
                q.where(p.ORG_ID.eq(orgId));
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

    public boolean hasAccessLevel(UUID projectId, UUID userId, ResourceAccessLevel... levels) {
        try (DSLContext tx = DSL.using(cfg)) {
            return hasAccessLevel(tx, projectId, userId, levels);
        }
    }

    public boolean hasAccessLevel(DSLContext tx, UUID projectId, UUID userId, ResourceAccessLevel... levels) {
        SelectConditionStep<Record1<UUID>> teamIds = select(USER_TEAMS.TEAM_ID)
                .from(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(userId));

        return tx.fetchExists(selectFrom(PROJECT_TEAM_ACCESS)
                .where(PROJECT_TEAM_ACCESS.PROJECT_ID.eq(projectId)
                        .and(PROJECT_TEAM_ACCESS.TEAM_ID.in(teamIds))
                        .and(PROJECT_TEAM_ACCESS.ACCESS_LEVEL.in(Utils.toString(levels)))));
    }

    public void upsertAccessLevel(UUID projectId, UUID teamId, ResourceAccessLevel level) {
        tx(tx -> upsertAccessLevel(tx, projectId, teamId, level));
    }

    public void upsertAccessLevel(DSLContext tx, UUID projectId, UUID teamId, ResourceAccessLevel level) {
        tx.insertInto(PROJECT_TEAM_ACCESS)
                .columns(PROJECT_TEAM_ACCESS.PROJECT_ID, PROJECT_TEAM_ACCESS.TEAM_ID, PROJECT_TEAM_ACCESS.ACCESS_LEVEL)
                .values(projectId, teamId, level.toString())
                .onDuplicateKeyUpdate()
                .set(PROJECT_TEAM_ACCESS.ACCESS_LEVEL, level.toString())
                .execute();
    }

    public byte[] getOrUpdateSecretKey(UUID projectId, Supplier<byte[]> keySupplier) {
        return txResult(tx -> {
            byte[] data = tx.select(PROJECTS.SECRET_KEY).from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .fetchOne(PROJECTS.SECRET_KEY);

            // fast path
            if (data != null) {
                return data;
            }

            // add the key if not exists
            data = tx.select(PROJECTS.SECRET_KEY).from(PROJECTS)
                    .where(PROJECTS.PROJECT_ID.eq(projectId))
                    .forUpdate()
                    .fetchOne(PROJECTS.SECRET_KEY);

            if (data == null) {
                data = keySupplier.get();

                tx.update(PROJECTS)
                        .set(PROJECTS.SECRET_KEY, data)
                        .where(PROJECTS.PROJECT_ID.eq(projectId))
                        .execute();
            }

            return data;
        });
    }

    public List<ResourceAccessEntry> getAccessLevel(UUID projectId) {
        List<ResourceAccessEntry> resourceAccessList = new ArrayList<>();
        try (DSLContext tx = DSL.using(cfg)) {
            Result<Record5<UUID, UUID, String, String, String>> teamsAccess = tx.select(
                    PROJECT_TEAM_ACCESS.TEAM_ID,
                    PROJECT_TEAM_ACCESS.PROJECT_ID,
                    TEAMS.TEAM_NAME,
                    ORGANIZATIONS.ORG_NAME,
                    PROJECT_TEAM_ACCESS.ACCESS_LEVEL)
                    .from(PROJECT_TEAM_ACCESS)
                    .leftOuterJoin(TEAMS).on(TEAMS.TEAM_ID.eq(PROJECT_TEAM_ACCESS.TEAM_ID))
                    .leftOuterJoin(PROJECTS).on(PROJECTS.PROJECT_ID.eq(projectId))
                    .leftOuterJoin(ORGANIZATIONS).on(ORGANIZATIONS.ORG_ID.eq(PROJECTS.ORG_ID))
                    .where(PROJECT_TEAM_ACCESS.PROJECT_ID.eq(projectId))
                    .fetch();

            for (Record5<UUID, UUID, String, String, String> t : teamsAccess) {
                resourceAccessList.add(new ResourceAccessEntry(t.get(PROJECT_TEAM_ACCESS.TEAM_ID),
                        t.get(ORGANIZATIONS.ORG_NAME),
                        t.get(TEAMS.TEAM_NAME),
                        ResourceAccessLevel.valueOf(t.get(PROJECT_TEAM_ACCESS.ACCESS_LEVEL))));
            }
        }
        return resourceAccessList;
    }

    public void deleteTeamAccess(DSLContext tx, UUID projectId) {
        tx.deleteFrom(PROJECT_TEAM_ACCESS)
                .where(PROJECT_TEAM_ACCESS.PROJECT_ID.eq(projectId))
                .execute();
    }

    private String serialize(Map<String, Object> m) {
        if (m == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(m);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }

    private static ProjectEntry toEntry(Record9<UUID, String, String, UUID, String, String, UUID, String, Boolean> r) {
        return new ProjectEntry(r.get(PROJECTS.PROJECT_ID),
                r.get(PROJECTS.PROJECT_NAME),
                r.get(PROJECTS.DESCRIPTION),
                r.get(PROJECTS.ORG_ID),
                r.value5(),
                null,
                null,
                ProjectVisibility.valueOf(r.get(PROJECTS.VISIBILITY)),
                toOwner(r.get(PROJECTS.OWNER_ID), r.value8()),
                r.get(PROJECTS.ACCEPTS_RAW_PAYLOAD),
                null);
    }

    private static ProjectOwner toOwner(UUID id, String username) {
        if (id == null) {
            return null;
        }
        return new ProjectOwner(id, username);
    }
}
