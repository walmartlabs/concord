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

import com.walmartlabs.concord.common.secret.HashAlgorithm;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.tables.Organizations;
import com.walmartlabs.concord.server.jooq.tables.Secrets;
import com.walmartlabs.concord.server.jooq.tables.Users;
import com.walmartlabs.concord.server.jooq.tables.records.SecretsRecord;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;
import org.jooq.exception.DataAccessException;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.PROJECT_SECRETS;
import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.SecretTeamAccess.SECRET_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

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

    public UUID getId(DSLContext tx, UUID orgId, String name) {
        return tx.select(SECRETS.SECRET_ID)
                .from(SECRETS)
                .where(SECRETS.ORG_ID.eq(orgId)
                        .and(SECRETS.SECRET_NAME.eq(name)))
                .fetchOne(SECRETS.SECRET_ID);
    }

    public UUID getId(UUID orgId, String name) {
        return getId(dsl(), orgId, name);
    }

    public String getName(UUID id) {
        return dsl().select(SECRETS.SECRET_NAME)
                .from(SECRETS)
                .where(SECRETS.SECRET_ID.eq(id))
                .fetchOne(SECRETS.SECRET_NAME);
    }

    public UUID getOrgId(UUID id) {
        return dsl().select(SECRETS.ORG_ID)
                .from(SECRETS)
                .where(SECRETS.SECRET_ID.eq(id))
                .fetchOne(SECRETS.ORG_ID);
    }

    public UUID insert(UUID orgId, String name, UUID ownerId, SecretType type,
                       SecretEncryptedByType encryptedBy, String storeType,
                       SecretVisibility visibility,
                       byte[] secretSalt, HashAlgorithm hashAlgorithm, InsertMode insertMode) {

        return txResult(tx -> insert(tx, orgId, name, ownerId, type, encryptedBy, storeType, visibility, secretSalt, hashAlgorithm, insertMode));

    }

    public UUID insert(DSLContext tx, UUID orgId, String name, UUID ownerId, SecretType type,
                       SecretEncryptedByType encryptedBy, String storeType,
                       SecretVisibility visibility, byte[] secretSalt, HashAlgorithm hashAlgorithm, InsertMode insertMode) {

        InsertOnDuplicateStep<SecretsRecord> builder = tx.insertInto(SECRETS)
                .columns(SECRETS.SECRET_NAME,
                        SECRETS.SECRET_TYPE,
                        SECRETS.ORG_ID,
                        SECRETS.OWNER_ID,
                        SECRETS.ENCRYPTED_BY,
                        SECRETS.STORE_TYPE,
                        SECRETS.VISIBILITY,
                        SECRETS.SECRET_SALT,
                        SECRETS.HASH_ALGORITHM)
                .values(name, type.toString(), orgId, ownerId, encryptedBy.toString(), storeType, visibility.toString(), secretSalt, hashAlgorithm.getName());

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

    public SecretEntryV2 get(UUID id) {
        DSLContext tx = dsl();
        return selectEntry(tx)
                .where(SECRETS.SECRET_ID.eq(id))
                .fetchOne(secretRecord -> toEntry(tx, secretRecord));
    }

    public byte[] getData(UUID id) {
        return dsl().select(SECRETS.SECRET_DATA)
                .from(SECRETS)
                .where(SECRETS.SECRET_ID.eq(id))
                .fetchOne(SECRETS.SECRET_DATA);
    }

    public SecretEntryV2 getByName(UUID orgId, String name) {
        DSLContext tx = dsl();
        return selectEntry(tx)
                .where(SECRETS.ORG_ID.eq(orgId)
                        .and(SECRETS.SECRET_NAME.eq(name)))
                .fetchOne(secretRecord -> toEntry(tx, secretRecord));
    }

    public void updateProjectScopeByProjectId(DSLContext tx, UUID orgId, UUID projectId, UUID newProjectId) {
        if (newProjectId == null) {
            tx.deleteFrom(PROJECT_SECRETS).where(PROJECT_SECRETS.PROJECT_ID.eq(projectId));
        } else {
            tx.update(PROJECT_SECRETS).set(PROJECT_SECRETS.PROJECT_ID, newProjectId).where(PROJECT_SECRETS.PROJECT_ID.eq(projectId));
        }
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

    public void update(UUID id, String newName, UUID ownerId, SecretType newType, byte[] data, SecretVisibility visibility, UUID orgId, HashAlgorithm hashAlgorithm) {
        tx(tx -> update(tx, id, newName, ownerId, newType, data, visibility, orgId, hashAlgorithm));
    }

    public void updateSecretProjects(UUID id, Set<UUID> projectIds) {
        tx(tx -> updateSecretProjects(tx, id, projectIds));
    }

    public void updateSecretProjects(DSLContext tx, UUID id, Set<UUID> projectIds) {
        if (projectIds != null) {
            tx.deleteFrom(PROJECT_SECRETS).where(PROJECT_SECRETS.SECRET_ID.eq(id)).execute();
            for (UUID projectId : projectIds) {
                tx.insertInto(PROJECT_SECRETS).columns(PROJECT_SECRETS.SECRET_ID, PROJECT_SECRETS.PROJECT_ID).values(id, projectId).execute();
            }
        }
    }

    public void update(DSLContext tx, UUID id, String newName, UUID ownerId, SecretType newType, byte[] data, SecretVisibility visibility, UUID orgId, HashAlgorithm hashAlgorithm) {
        UpdateSetFirstStep<SecretsRecord> u = tx.update(SECRETS);
        boolean needUpdate = false;
        if (newName != null) {
            u.set(SECRETS.SECRET_NAME, newName);
            needUpdate = true;
        }

        if (ownerId != null) {
            u.set(SECRETS.OWNER_ID, ownerId);
            needUpdate = true;
        }

        if (visibility != null) {
            u.set(SECRETS.VISIBILITY, visibility.toString());
            needUpdate = true;
        }

        if (newType != null) {
            u.set(SECRETS.SECRET_TYPE, newType.name());
            needUpdate = true;
        }

        if (data != null) {
            u.set(SECRETS.SECRET_DATA, data);
            u.set(SECRETS.LAST_UPDATED_AT, currentOffsetDateTime());
            needUpdate = true;
        }

        if (orgId != null) {
            u.set(SECRETS.ORG_ID, orgId);
            needUpdate = true;
        }

        if (hashAlgorithm != null) {
            u.set(SECRETS.HASH_ALGORITHM, hashAlgorithm.getName());
            needUpdate = true;
        }
        if (needUpdate) {
            int i = ((UpdateSetMoreStep) u).where(SECRETS.SECRET_ID.eq(id))
                    .execute();

            if (i != 1) {
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }
        }

    }

    public List<SecretEntryV2> list(UUID orgId, UUID currentUserId, Field<?> sortField, boolean asc, int offset, int limit, String filter) {

        Organizations o = ORGANIZATIONS.as("o");
        Secrets s = SECRETS.as("s");
        Users u = USERS.as("u");

        sortField = s.field(sortField);

        DSLContext tx = dsl();
        SelectOnConditionStep<Record17<UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String, byte[], String, OffsetDateTime, OffsetDateTime>> q = selectEntry(tx, o, s, u);

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

        return q.fetch(secretRecord -> toEntry(tx, secretRecord));
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

        Result<Record4<UUID, UUID, String, String>> teamAccess = dsl().select(
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

        return resourceAccessList;
    }

    public void deleteTeamAccess(DSLContext tx, UUID secretId) {
        tx.deleteFrom(SECRET_TEAM_ACCESS)
                .where(SECRET_TEAM_ACCESS.SECRET_ID.eq(secretId))
                .execute();
    }

    public boolean hasAccessLevel(UUID secretId, UUID userId, ResourceAccessLevel... levels) {
        return hasAccessLevel(dsl(), secretId, userId, levels);
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

    private static SelectOnConditionStep<Record17<UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String, byte[], String, OffsetDateTime, OffsetDateTime>> selectEntry(DSLContext tx) {
        return selectEntry(tx, ORGANIZATIONS, SECRETS, USERS);
    }

    private static SelectOnConditionStep<Record17<UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String, byte[], String, OffsetDateTime, OffsetDateTime>> selectEntry(
            DSLContext tx,
            Organizations orgAlias,
            Secrets secretAlias, Users userAlias) {
        return tx.select(secretAlias.SECRET_ID,
                        secretAlias.SECRET_NAME,
                        secretAlias.ORG_ID,
                        orgAlias.ORG_NAME,
                        secretAlias.SECRET_TYPE,
                        secretAlias.ENCRYPTED_BY,
                        secretAlias.STORE_TYPE,
                        secretAlias.VISIBILITY,
                        userAlias.USER_ID,
                        userAlias.USERNAME,
                        userAlias.DOMAIN,
                        userAlias.DISPLAY_NAME,
                        userAlias.USER_TYPE,
                        secretAlias.SECRET_SALT,
                        secretAlias.HASH_ALGORITHM,
                        secretAlias.CREATED_AT,
                        secretAlias.LAST_UPDATED_AT
                )

                .from(secretAlias)
                .leftJoin(userAlias).on(secretAlias.OWNER_ID.eq(userAlias.USER_ID))
                .leftJoin(orgAlias).on(orgAlias.ORG_ID.eq(secretAlias.ORG_ID));
    }

    private static SecretEntryV2 toEntry(DSLContext tx, Record17<UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String, byte[], String, OffsetDateTime, OffsetDateTime> r) {
        UUID secretId = r.get(SECRETS.SECRET_ID);
        Set<ProjectEntry> projects = tx.select(PROJECTS.PROJECT_ID, PROJECTS.PROJECT_NAME).from(PROJECTS).leftJoin(PROJECT_SECRETS).on(PROJECTS.PROJECT_ID.eq(PROJECT_SECRETS.PROJECT_ID))
                .where(PROJECT_SECRETS.SECRET_ID.eq(secretId)).stream()
                .map(projectRecord -> new ProjectEntry(projectRecord.get(PROJECTS.PROJECT_NAME), projectRecord.get(PROJECTS.PROJECT_ID))).collect(Collectors.toSet());
        return new SecretEntryV2(r.get(SECRETS.SECRET_ID),
                r.get(SECRETS.SECRET_NAME),
                r.get(SECRETS.ORG_ID),
                r.value4(),
                projects,
                SecretType.valueOf(r.get(SECRETS.SECRET_TYPE)),
                SecretEncryptedByType.valueOf(r.get(SECRETS.ENCRYPTED_BY)),
                r.get(SECRETS.STORE_TYPE),
                SecretVisibility.valueOf(r.get(SECRETS.VISIBILITY)),
                toOwner(r),
                r.get(SECRETS.CREATED_AT),
                r.get(SECRETS.LAST_UPDATED_AT),
                r.get(SECRETS.SECRET_SALT),
                HashAlgorithm.getByName(r.get(SECRETS.HASH_ALGORITHM))
        );
    }

    private static EntityOwner toOwner(Record17<UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String, byte[], String, OffsetDateTime, OffsetDateTime> r) {

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

    public static class SecretDataEntry extends SecretEntryV2 {

        private static final long serialVersionUID = 1L;

        private final byte[] data;

        public SecretDataEntry(SecretEntryV2 s, byte[] data) { // NOSONAR
            super(s.getId(), s.getName(), s.getOrgId(), s.getOrgName(), s.getProjects(),
                    s.getType(), s.getEncryptedBy(), s.getStoreType(), s.getVisibility(), s.getOwner(),
                    s.getCreatedAt(), s.getLastUpdatedAt(), s.getSecretSalt(), s.getHashAlgorithm());
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }
}
