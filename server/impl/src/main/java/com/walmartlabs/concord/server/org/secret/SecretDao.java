package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.tables.Organizations;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.jooq.tables.Secrets;
import com.walmartlabs.concord.server.jooq.tables.Users;
import com.walmartlabs.concord.server.jooq.tables.records.SecretsRecord;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.SecretTeamAccess.SECRET_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

@Named
public class SecretDao extends AbstractDao {

    public enum InsertMode {
        INSERT,
        UPSERT
    }

    @Inject
    public SecretDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    @Override
    protected <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public UUID getId(UUID orgId, String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(SECRETS.SECRET_ID)
                    .from(SECRETS)
                    .where(SECRETS.ORG_ID.eq(orgId)
                            .and(SECRETS.SECRET_NAME.eq(name)))
                    .fetchOne(SECRETS.SECRET_ID);
        }
    }

    public String getName(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(SECRETS.SECRET_NAME)
                    .from(SECRETS)
                    .where(SECRETS.SECRET_ID.eq(id))
                    .fetchOne(SECRETS.SECRET_NAME);
        }
    }

    public UUID getOrgId(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(SECRETS.ORG_ID)
                    .from(SECRETS)
                    .where(SECRETS.SECRET_ID.eq(id))
                    .fetchOne(SECRETS.ORG_ID);
        }
    }

    public UUID insert(UUID orgId, UUID projectId, String name, UUID ownerId, SecretType type,
                       SecretEncryptedByType encryptedBy, String storeType,
                       SecretVisibility visibility, InsertMode insertMode) {

        return txResult(tx -> insert(tx, orgId, projectId, name, ownerId, type, encryptedBy, storeType, visibility, insertMode));
    }

    public UUID insert(DSLContext tx, UUID orgId, UUID projectId, String name, UUID ownerId, SecretType type,
                       SecretEncryptedByType encryptedBy, String storeType,
                       SecretVisibility visibility, InsertMode insertMode) {

        InsertOnDuplicateStep<SecretsRecord> builder = tx.insertInto(SECRETS)
                .columns(SECRETS.SECRET_NAME,
                        SECRETS.SECRET_TYPE,
                        SECRETS.ORG_ID,
                        SECRETS.PROJECT_ID,
                        SECRETS.OWNER_ID,
                        SECRETS.ENCRYPTED_BY,
                        SECRETS.STORE_TYPE,
                        SECRETS.VISIBILITY)
                .values(name, type.toString(), orgId, projectId, ownerId, encryptedBy.toString(), storeType, visibility.toString());

        if (insertMode == InsertMode.UPSERT) {
            Optional<SecretsRecord> secretsRecord = builder.onDuplicateKeyIgnore()
                    .returning(SECRETS.SECRET_ID)
                    .fetchOptional();
            return secretsRecord.map(SecretsRecord::getSecretId).orElseGet(() -> tx.select(SECRETS.SECRET_ID)
                    .from(SECRETS)
                    .where(SECRETS.SECRET_NAME.eq(name).and(SECRETS.ORG_ID.eq(orgId)))
                    .fetchOne()
                    .value1());
        }

        return builder
                .returning(SECRETS.SECRET_ID)
                .fetchOne()
                .getSecretId();
    }

    public SecretEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectEntry(tx)
                    .where(SECRETS.SECRET_ID.eq(id))
                    .fetchOne(SecretDao::toEntry);
        }
    }

    public byte[] getData(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(SECRETS.SECRET_DATA)
                    .from(SECRETS)
                    .where(SECRETS.SECRET_ID.eq(id))
                    .fetchOne(SECRETS.SECRET_DATA);
        }
    }

    public SecretEntry getByName(UUID orgId, String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectEntry(tx)
                    .where(SECRETS.ORG_ID.eq(orgId)
                            .and(SECRETS.SECRET_NAME.eq(name)))
                    .fetchOne(SecretDao::toEntry);
        }
    }

    public void updateProjectScopeByProjectId(DSLContext tx, UUID orgId, UUID projectId, UUID newProjectId) {
        tx.update(SECRETS)
                .set(SECRETS.PROJECT_ID, newProjectId)
                .where(SECRETS.ORG_ID.eq(orgId))
                .and(SECRETS.PROJECT_ID.eq(projectId))
                .execute();
    }

    public void updateData(UUID id, byte[] data) {
        tx(tx -> updateData(tx, id, data));
    }

    public void updateData(DSLContext tx, UUID id, byte[] data) {
        int i = tx.update(SECRETS)
                .set(SECRETS.SECRET_DATA, data)
                .where(SECRETS.SECRET_ID.eq(id))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public void update(UUID id, String newName, UUID ownerId, byte[] data, SecretVisibility visibility, UUID projectId, UUID orgId) {
        tx(tx -> update(tx, id, newName, ownerId, data, visibility, projectId, orgId));
    }

    public void update(DSLContext tx, UUID id, String newName, UUID ownerId, byte[] data, SecretVisibility visibility, UUID projectId, UUID orgId) {
        UpdateSetMoreStep<SecretsRecord> u = tx.update(SECRETS).set(SECRETS.PROJECT_ID, projectId);

        if (newName != null) {
            u.set(SECRETS.SECRET_NAME, newName);
        }

        if (ownerId != null) {
            u.set(SECRETS.OWNER_ID, ownerId);
        }

        if (visibility != null) {
            u.set(SECRETS.VISIBILITY, visibility.toString());
        }

        if (data != null) {
            u.set(SECRETS.SECRET_DATA, data);
        }

        if (orgId != null) {
            u.set(SECRETS.ORG_ID, orgId);
        }

        int i = u.where(SECRETS.SECRET_ID.eq(id))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public void update(DSLContext tx, UUID id, UUID orgId, UUID projectId, String name, SecretType type, SecretEncryptedByType encryptedByType, SecretVisibility visibility, byte[] data) {
        int i = tx.update(SECRETS)
                .set(SECRETS.ORG_ID, orgId)
                .set(SECRETS.PROJECT_ID, projectId)
                .set(SECRETS.SECRET_NAME, name)
                .set(SECRETS.SECRET_TYPE, type.toString())
                .set(SECRETS.ENCRYPTED_BY, encryptedByType.toString())
                .set(SECRETS.SECRET_DATA, data)
                .set(SECRETS.VISIBILITY, visibility.toString())
                .where(SECRETS.SECRET_ID.eq(id))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public List<SecretEntry> list(UUID orgId, UUID currentUserId, Field<?> sortField, boolean asc, int offset, int limit, String filter) {

        Organizations o = ORGANIZATIONS.as("o");
        Secrets s = SECRETS.as("s");
        Projects p = PROJECTS.as("p");
        Users u = USERS.as("u");

        sortField = s.field(sortField);

        try (DSLContext tx = DSL.using(cfg)) {
            SelectOnConditionStep<Record15<UUID, String, UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String>> q = selectEntry(tx, o, s, p, u);

            if (currentUserId != null) {
                // public secrets are visible for anyone
                Condition isPublic = s.VISIBILITY.eq(SecretVisibility.PUBLIC.toString());

                // check if the user belongs to a team in the org
                SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                        .from(TEAMS)
                        .where(TEAMS.ORG_ID.eq(orgId));

                Condition isInATeam = exists(selectOne().from(V_USER_TEAMS)
                        .where(V_USER_TEAMS.USER_ID.eq(currentUserId)
                                .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));

                // check if the user owns secrets in the org
                Condition ownsSecrets = s.OWNER_ID.eq(currentUserId);

                // check if the user owns the org
                Condition ownsOrg = o.OWNER_ID.eq(currentUserId);

                // if any of those conditions true then the secret must be visible
                q.where(or(isPublic, isInATeam, ownsSecrets, ownsOrg));
            }

            if (orgId != null) {
                q.where(s.ORG_ID.eq(orgId));
            }

            if (filter != null) {
                q.where(s.SECRET_NAME.containsIgnoreCase(filter));
            }

            if (sortField != null) {
                q.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            if (offset >= 0) {
                q.offset(offset);
            }

            if (limit > 0) {
                q.limit(limit);
            }

            return q.fetch(SecretDao::toEntry);
        }
    }

    public void delete(UUID id) {
        tx(tx -> delete(tx, id));
    }

    public void delete(DSLContext tx, UUID id) {
        tx.deleteFrom(SECRETS)
                .where(SECRETS.SECRET_ID.eq(id))
                .execute();
    }

    public List<ResourceAccessEntry> getAccessLevel(UUID orgId, String name) {
        List<ResourceAccessEntry> resourceAccessList = new ArrayList<>();
        try (DSLContext tx = DSL.using(cfg)) {
            Result<Record4<UUID, UUID, String, String>> teamAccess = tx.select(
                    SECRET_TEAM_ACCESS.TEAM_ID,
                    SECRET_TEAM_ACCESS.SECRET_ID,
                    TEAMS.TEAM_NAME,
                    SECRET_TEAM_ACCESS.ACCESS_LEVEL)
                    .from(SECRET_TEAM_ACCESS)
                    .leftOuterJoin(TEAMS).on(TEAMS.TEAM_ID.eq(SECRET_TEAM_ACCESS.TEAM_ID))
                    .where(SECRET_TEAM_ACCESS.SECRET_ID.eq(getByName(orgId, name).getId()))
                    .fetch();

            for (Record4<UUID, UUID, String, String> t : teamAccess) {
                resourceAccessList.add(new ResourceAccessEntry(t.get(SECRET_TEAM_ACCESS.TEAM_ID),
                        null,
                        t.get(TEAMS.TEAM_NAME),
                        ResourceAccessLevel.valueOf(t.get(SECRET_TEAM_ACCESS.ACCESS_LEVEL))));
            }

        }
        return resourceAccessList;
    }

    public void deleteTeamAccess(DSLContext tx, UUID secretId) {
        tx.deleteFrom(SECRET_TEAM_ACCESS)
                .where(SECRET_TEAM_ACCESS.SECRET_ID.eq(secretId))
                .execute();
    }

    public boolean hasAccessLevel(UUID secretId, UUID userId, ResourceAccessLevel... levels) {
        try (DSLContext tx = DSL.using(cfg)) {
            return hasAccessLevel(tx, secretId, userId, levels);
        }
    }

    private boolean hasAccessLevel(DSLContext tx, UUID secretId, UUID userId, ResourceAccessLevel... levels) {
        SelectConditionStep<Record1<UUID>> teamIds = select(V_USER_TEAMS.TEAM_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(userId));

        return tx.fetchExists(selectFrom(SECRET_TEAM_ACCESS)
                .where(SECRET_TEAM_ACCESS.SECRET_ID.eq(secretId)
                        .and(SECRET_TEAM_ACCESS.TEAM_ID.in(teamIds))
                        .and(SECRET_TEAM_ACCESS.ACCESS_LEVEL.in(Utils.toString(levels)))));
    }

    public void upsertAccessLevel(UUID secretId, UUID teamId, ResourceAccessLevel level) {
        tx(tx -> upsertAccessLevel(tx, secretId, teamId, level));
    }

    public void upsertAccessLevel(DSLContext tx, UUID secretId, UUID teamId, ResourceAccessLevel level) {
        tx.insertInto(SECRET_TEAM_ACCESS)
                .columns(SECRET_TEAM_ACCESS.SECRET_ID, SECRET_TEAM_ACCESS.TEAM_ID, SECRET_TEAM_ACCESS.ACCESS_LEVEL)
                .values(secretId, teamId, level.toString())
                .onDuplicateKeyUpdate()
                .set(SECRET_TEAM_ACCESS.ACCESS_LEVEL, level.toString())
                .execute();
    }

    private static SelectOnConditionStep<Record15<UUID, String, UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String>> selectEntry(DSLContext tx) {
        return selectEntry(tx, ORGANIZATIONS, SECRETS, PROJECTS, USERS);
    }

    private static SelectOnConditionStep<Record15<UUID, String, UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String>> selectEntry(DSLContext tx,
                                                                                                                                                                               Organizations orgAlias,
                                                                                                                                                                               Secrets secretAlias,
                                                                                                                                                                               Projects projectAlias,
                                                                                                                                                                               Users userAlias) {
        return tx.select(secretAlias.SECRET_ID,
                secretAlias.SECRET_NAME,
                secretAlias.ORG_ID,
                orgAlias.ORG_NAME,
                secretAlias.PROJECT_ID,
                projectAlias.PROJECT_NAME,
                secretAlias.SECRET_TYPE,
                secretAlias.ENCRYPTED_BY,
                secretAlias.STORE_TYPE,
                secretAlias.VISIBILITY,
                userAlias.USER_ID,
                userAlias.USERNAME,
                userAlias.DOMAIN,
                userAlias.DISPLAY_NAME,
                userAlias.USER_TYPE)
                .from(secretAlias)
                .leftJoin(userAlias).on(secretAlias.OWNER_ID.eq(userAlias.USER_ID))
                .leftJoin(projectAlias).on(projectAlias.PROJECT_ID.eq(secretAlias.PROJECT_ID))
                .leftJoin(orgAlias).on(orgAlias.ORG_ID.eq(secretAlias.ORG_ID));
    }

    private static SecretEntry toEntry(Record15<UUID, String, UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String> r) {
        return new SecretEntry(r.get(SECRETS.SECRET_ID),
                r.get(SECRETS.SECRET_NAME),
                r.get(SECRETS.ORG_ID),
                r.value4(),
                r.get(SECRETS.PROJECT_ID),
                r.value6(),
                SecretType.valueOf(r.get(SECRETS.SECRET_TYPE)),
                SecretEncryptedByType.valueOf(r.get(SECRETS.ENCRYPTED_BY)),
                r.get(SECRETS.STORE_TYPE),
                SecretVisibility.valueOf(r.get(SECRETS.VISIBILITY)),
                toOwner(r));
    }

    private static EntityOwner toOwner(Record15<UUID, String, UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String> r) {
        UUID id = r.get(USERS.USER_ID);
        if (id == null) {
            return null;
        }

        return EntityOwner.builder()
                .id(id)
                .username(r.get(USERS.USERNAME))
                .userDomain(r.get(USERS.DOMAIN))
                .displayName(r.get(USERS.DISPLAY_NAME))
                .userType(UserType.valueOf(r.get(USERS.USER_TYPE)))
                .build();
    }

    public static class SecretDataEntry extends SecretEntry {

        private static final long serialVersionUID = 1L;

        private final byte[] data;

        public SecretDataEntry(SecretEntry s, byte[] data) { // NOSONAR
            this(s.getId(), s.getName(), s.getOrgId(), s.getOrgName(), s.getProjectId(), s.getProjectName(),
                    s.getType(), s.getEncryptedBy(), s.getStoreType(), s.getVisibility(), s.getOwner(), data);
        }

        public SecretDataEntry(UUID id, String name, UUID orgId, String orgName, UUID projectId, String projectName, SecretType type,
                               SecretEncryptedByType encryptedByType, String storeType, SecretVisibility visibility,
                               EntityOwner owner, byte[] data) { // NOSONAR

            super(id, name, orgId, orgName, projectId, projectName, type, encryptedByType, storeType, visibility, owner);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }
}
