package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.enums.OutVariablesMode;
import com.walmartlabs.concord.server.jooq.enums.ProcessExecMode;
import com.walmartlabs.concord.server.jooq.enums.RawPayloadMode;
import com.walmartlabs.concord.server.jooq.tables.Organizations;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.Users;
import com.walmartlabs.concord.server.jooq.tables.records.ProjectsRecord;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.ProjectKvStore.PROJECT_KV_STORE;
import static com.walmartlabs.concord.server.jooq.tables.ProjectTeamAccess.PROJECT_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

public class ProjectDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public ProjectDao(@MainDB Configuration cfg,
                      ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
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
        return dsl().select(PROJECTS.PROJECT_NAME)
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(projectId))
                .fetchOne(PROJECTS.PROJECT_NAME);
    }

    public UUID getId(UUID orgId, String projectName) {
        return getId(dsl(), orgId, projectName);
    }

    public UUID getId(DSLContext tx, UUID orgId, String projectName) {
        return tx.select(PROJECTS.PROJECT_ID)
                .from(PROJECTS)
                .where(PROJECTS.ORG_ID.eq(orgId)
                        .and(PROJECTS.PROJECT_NAME.eq(projectName)))
                .fetchOne(PROJECTS.PROJECT_ID);
    }

    public UUID getOrgId(UUID projectId) {
        return dsl().select(PROJECTS.ORG_ID)
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(projectId))
                .fetchOne(PROJECTS.ORG_ID);
    }

    public String getOrgName(UUID projectId) {
        return dsl().select(ORGANIZATIONS.ORG_ID)
                .from(PROJECTS, ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(PROJECTS.ORG_ID)
                        .and(PROJECTS.PROJECT_ID.eq(projectId)))
                .fetchOne(ORGANIZATIONS.ORG_NAME);
    }

    public ProjectEntry get(UUID projectId) {
        return get(dsl(), projectId);
    }

    public ProjectEntry get(DSLContext tx, UUID projectId) {
        Projects p = PROJECTS.as("p");
        Users u = USERS.as("u");

        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME).from(ORGANIZATIONS).where(ORGANIZATIONS.ORG_ID.eq(p.ORG_ID)).asField();

        Record17<UUID, String, String, UUID, String, JSONB, String, UUID, String, String, String, String, RawPayloadMode, JSONB, OutVariablesMode, ProcessExecMode, OffsetDateTime> r = tx.select(
                p.PROJECT_ID,
                p.PROJECT_NAME,
                p.DESCRIPTION,
                p.ORG_ID,
                orgNameField,
                p.PROJECT_CFG,
                p.VISIBILITY,
                p.OWNER_ID,
                u.USERNAME,
                u.DOMAIN,
                u.USER_TYPE,
                u.DISPLAY_NAME,
                p.RAW_PAYLOAD_MODE,
                p.META,
                p.OUT_VARIABLES_MODE,
                p.PROCESS_EXEC_MODE,
                p.CREATED_AT)
                .from(p)
                .leftJoin(u).on(u.USER_ID.eq(p.OWNER_ID))
                .where(p.PROJECT_ID.eq(projectId))
                .fetchOne();

        if (r == null) {
            return null;
        }

        Map<String, Object> cfg = objectMapper.fromJSONB(r.get(p.PROJECT_CFG));

        return new ProjectEntry(projectId,
                r.get(p.PROJECT_NAME),
                r.get(p.DESCRIPTION),
                r.get(p.ORG_ID),
                r.get(orgNameField),
                null,
                cfg,
                ProjectVisibility.valueOf(r.get(p.VISIBILITY)),
                toOwner(r.get(p.OWNER_ID), r.get(u.USERNAME), r.get(u.DOMAIN), r.get(u.DISPLAY_NAME), r.get(u.USER_TYPE)),
                r.get(p.RAW_PAYLOAD_MODE) != RawPayloadMode.DISABLED,
                r.get(p.RAW_PAYLOAD_MODE),
                objectMapper.fromJSONB(r.get(p.META)),
                r.get(p.OUT_VARIABLES_MODE),
                r.get(p.PROCESS_EXEC_MODE),
                r.get(p.CREATED_AT));
    }

    public UUID insert(UUID orgId, String name, String description, UUID ownerId, Map<String, Object> cfg,
                       ProjectVisibility visibility, RawPayloadMode rawPayloadMode, byte[] encryptedKey, Map<String, Object> meta,
                       OutVariablesMode outVariablesMode, ProcessExecMode processExecMode) {

        return txResult(tx -> insert(tx, orgId, name, description, ownerId, cfg, visibility, rawPayloadMode, encryptedKey, meta, outVariablesMode, processExecMode));
    }

    public UUID insert(DSLContext tx, UUID orgId, String name, String description, UUID ownerId, Map<String, Object> cfg,
                       ProjectVisibility visibility, RawPayloadMode rawPayloadMode, byte[] encryptedKey, Map<String, Object> meta,
                       OutVariablesMode outVariablesMode, ProcessExecMode processExecMode) {

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
                        PROJECTS.RAW_PAYLOAD_MODE,
                        PROJECTS.SECRET_KEY,
                        PROJECTS.META,
                        PROJECTS.OUT_VARIABLES_MODE,
                        PROJECTS.PROCESS_EXEC_MODE,
                        PROJECTS.CREATED_AT)
                .values(value(name),
                        value(description),
                        value(orgId),
                        value(objectMapper.toJSONB(cfg)),
                        value(visibility.toString()),
                        value(ownerId),
                        value(rawPayloadMode != null ? rawPayloadMode : RawPayloadMode.DISABLED),
                        value(encryptedKey),
                        value(objectMapper.toJSONB(meta)),
                        value(outVariablesMode != null ? outVariablesMode : OutVariablesMode.DISABLED),
                        value(processExecMode != null ? processExecMode : ProcessExecMode.READERS),
                        currentOffsetDateTime())
                .returning(PROJECTS.PROJECT_ID)
                .fetchOne()
                .getProjectId();
    }

    public void update(DSLContext tx, UUID orgId, UUID id, ProjectVisibility visibility,
                       String name, String description, Map<String, Object> cfg, RawPayloadMode rawPayloadMode,
                       UUID ownerId, Map<String, Object> meta, OutVariablesMode outVariablesMode,
                       ProcessExecMode processExecMode) {

        UpdateSetFirstStep<ProjectsRecord> q = tx.update(PROJECTS);

        if (name != null) {
            q.set(PROJECTS.PROJECT_NAME, name);
        }

        if (description != null) {
            q.set(PROJECTS.DESCRIPTION, description);
        }

        if (cfg != null) {
            q.set(PROJECTS.PROJECT_CFG, objectMapper.toJSONB(cfg));
        }

        if (visibility != null) {
            q.set(PROJECTS.VISIBILITY, visibility.toString());
        }

        if (rawPayloadMode != null) {
            q.set(PROJECTS.RAW_PAYLOAD_MODE, rawPayloadMode);
        }

        if (ownerId != null) {
            q.set(PROJECTS.OWNER_ID, ownerId);
        }

        if (meta != null) {
            q.set(PROJECTS.META, objectMapper.toJSONB(meta));
        }

        if (outVariablesMode != null) {
            q.set(PROJECTS.OUT_VARIABLES_MODE, outVariablesMode);
        }

        if (processExecMode != null) {
            q.set(PROJECTS.PROCESS_EXEC_MODE, processExecMode);
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
                .set(PROJECTS.PROJECT_CFG, objectMapper.toJSONB(cfg))
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

    public List<ProjectEntry> list(UUID orgId, UUID currentUserId, Field<?> sortField,
                                   boolean asc, int offset, int limit, String filter) {

        Users u = USERS.as("u");
        Projects p = PROJECTS.as("p");
        Organizations o = ORGANIZATIONS.as("o");

        sortField = p.field(sortField);


        SelectOnConditionStep<Record15<UUID, String, String, UUID, String, String, UUID, String, String, String, String, RawPayloadMode, OutVariablesMode, ProcessExecMode, OffsetDateTime>> q = dsl().select(
                p.PROJECT_ID,
                p.PROJECT_NAME,
                p.DESCRIPTION,
                p.ORG_ID,
                o.ORG_NAME,
                p.VISIBILITY,
                p.OWNER_ID,
                u.USERNAME,
                u.DOMAIN,
                u.DISPLAY_NAME,
                u.USER_TYPE,
                p.RAW_PAYLOAD_MODE,
                p.OUT_VARIABLES_MODE,
                p.PROCESS_EXEC_MODE,
                p.CREATED_AT)
                .from(p)
                .leftJoin(u).on(u.USER_ID.eq(p.OWNER_ID))
                .leftJoin(o).on(o.ORG_ID.eq(p.ORG_ID));

        if (currentUserId != null) {
            // public projects are visible for anyone
            Condition isPublic = p.VISIBILITY.eq(ProjectVisibility.PUBLIC.toString());

            // check if the user belongs to a team in the org
            SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                    .from(TEAMS)
                    .where(TEAMS.ORG_ID.eq(orgId));

            Condition isInATeam = exists(selectOne().from(V_USER_TEAMS)
                    .where(V_USER_TEAMS.USER_ID.eq(currentUserId)
                            .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));

            // check if the user owns projects in the org
            Condition ownsProjects = p.OWNER_ID.eq(currentUserId);

            // check if the user owns the org
            Condition ownsOrg = o.OWNER_ID.eq(currentUserId);

            // if any of those conditions true then the project must be visible
            q.where(or(isPublic, isInATeam, ownsProjects, ownsOrg));
        }

        if (orgId != null) {
            q.where(p.ORG_ID.eq(orgId));
        }

        if (sortField != null) {
            q.orderBy(asc ? sortField.asc() : sortField.desc());
        }

        if (filter != null) {
            q.where(p.PROJECT_NAME.containsIgnoreCase(filter));
        }

        if (offset > 0) {
            q.offset(offset);
        }

        if (limit > 0) {
            q.limit(limit);
        }

        return q.fetch(ProjectDao::toEntry);
    }

    public Map<String, Object> getConfiguration(UUID projectId) {
        return dsl().select(PROJECTS.PROJECT_CFG)
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(projectId))
                .fetchOne(e -> objectMapper.fromJSONB(e.value1()));
    }

    public Object getConfigurationValue(UUID projectId, String... path) {
        Map<String, Object> cfg = getConfiguration(projectId);
        return ConfigurationUtils.get(cfg, path);
    }

    public boolean hasAccessLevel(UUID projectId, UUID userId, ResourceAccessLevel... levels) {
        return hasAccessLevel(dsl(), projectId, userId, levels);
    }

    public boolean hasAccessLevel(DSLContext tx, UUID projectId, UUID userId, ResourceAccessLevel... levels) {
        SelectConditionStep<Record1<UUID>> teamIds = select(V_USER_TEAMS.TEAM_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(userId));

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

        Result<Record5<UUID, UUID, String, String, String>> teamsAccess = dsl().select(
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

        return resourceAccessList;
    }

    public void deleteTeamAccess(DSLContext tx, UUID projectId) {
        tx.deleteFrom(PROJECT_TEAM_ACCESS)
                .where(PROJECT_TEAM_ACCESS.PROJECT_ID.eq(projectId))
                .execute();
    }

    private static ProjectEntry toEntry(Record15<UUID, String, String, UUID, String, String, UUID, String, String, String, String, RawPayloadMode, OutVariablesMode, ProcessExecMode, OffsetDateTime> r) {
        return new ProjectEntry(r.get(PROJECTS.PROJECT_ID),
                r.get(PROJECTS.PROJECT_NAME),
                r.get(PROJECTS.DESCRIPTION),
                r.get(PROJECTS.ORG_ID),
                r.value5(),
                null,
                null,
                ProjectVisibility.valueOf(r.get(PROJECTS.VISIBILITY)),
                toOwner(r.get(PROJECTS.OWNER_ID), r.get(USERS.USERNAME), r.get(USERS.DOMAIN), r.get(USERS.DISPLAY_NAME), r.get(USERS.USER_TYPE)),
                r.get(PROJECTS.RAW_PAYLOAD_MODE) != RawPayloadMode.DISABLED,
                r.get(PROJECTS.RAW_PAYLOAD_MODE),
                null,
                r.get(PROJECTS.OUT_VARIABLES_MODE),
                r.get(PROJECTS.PROCESS_EXEC_MODE),
                r.get(PROJECTS.CREATED_AT));
    }

    private static EntityOwner toOwner(UUID id, String username, String domain, String displayName, String userType) {
        if (id == null) {
            return null;
        }
        return EntityOwner.builder()
                .id(id)
                .username(username)
                .userDomain(domain)
                .displayName(displayName)
                .userType(UserType.valueOf(userType))
                .build();
    }
}
