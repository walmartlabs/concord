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
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROJECTS;
import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.SecretTeamAccess.SECRET_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

@Named
public class SecretDao extends AbstractDao {

    @Inject
    public SecretDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
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
                       SecretVisibility visibility) {

        return txResult(tx -> insert(tx, orgId, projectId, name, ownerId, type, encryptedBy, storeType, visibility));
    }

    public UUID insert(DSLContext tx, UUID orgId, UUID projectId, String name, UUID ownerId, SecretType type,
                       SecretEncryptedByType encryptedBy, String storeType,
                       SecretVisibility visibility) {

        return tx.insertInto(SECRETS)
                .columns(SECRETS.SECRET_NAME,
                        SECRETS.SECRET_TYPE,
                        SECRETS.ORG_ID,
                        SECRETS.PROJECT_ID,
                        SECRETS.OWNER_ID,
                        SECRETS.ENCRYPTED_BY,
                        SECRETS.STORE_TYPE,
                        SECRETS.VISIBILITY)
                .values(name, type.toString(), orgId, projectId, ownerId, encryptedBy.toString(), storeType, visibility.toString())
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

    private void updateData(DSLContext tx, UUID id, byte[] data) {
        int i = tx.update(SECRETS)
                .set(SECRETS.SECRET_DATA, data)
                .where(SECRETS.SECRET_ID.eq(id))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public void update(UUID id, String newName, byte[] data, SecretVisibility visibility, UUID projectId, UUID orgId) {
        tx(tx -> update(tx, id, newName, data, visibility, projectId, orgId));
    }

    public void update(DSLContext tx, UUID id, String newName, byte[] data, SecretVisibility visibility, UUID projectId, UUID orgId) {
        UpdateSetMoreStep<SecretsRecord> u = tx.update(SECRETS).set(SECRETS.PROJECT_ID, projectId);

        if (newName != null) {
            u.set(SECRETS.SECRET_NAME, newName);
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

        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                .from(TEAMS)
                .where(TEAMS.ORG_ID.eq(orgId));

        Condition filterByTeamMember = exists(selectOne().from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(currentUserId)
                        .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));

        try (DSLContext tx = DSL.using(cfg)) {
            SelectOnConditionStep<Record15<UUID, String, UUID, String, UUID, String, String, String, String, String, UUID, String, String, String, String>> query = selectEntry(tx);

            if (currentUserId != null) {
                query.where(or(SECRETS.VISIBILITY.eq(SecretVisibility.PUBLIC.toString()), filterByTeamMember));
            }


            if (orgId != null) {
                query.where(SECRETS.ORG_ID.eq(orgId));
            }

            if(filter != null) {
                query.where(SECRETS.SECRET_NAME.containsIgnoreCase(filter));
            }

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            if (offset >= 0) {
                query.offset(offset);
            }

            if (limit > 0) {
                query.limit(limit);
            }

            return query.fetch(SecretDao::toEntry);
        }
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(SECRETS)
                .where(SECRETS.SECRET_ID.eq(id))
                .execute());
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
        Field<String> orgName = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(SECRETS.ORG_ID)).asField();

        Field<String> projectName = select(PROJECTS.PROJECT_NAME)
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(SECRETS.PROJECT_ID)).asField();

        return tx.select(SECRETS.SECRET_ID,
                SECRETS.SECRET_NAME,
                SECRETS.ORG_ID,
                orgName,
                SECRETS.PROJECT_ID,
                projectName,
                SECRETS.SECRET_TYPE,
                SECRETS.ENCRYPTED_BY,
                SECRETS.STORE_TYPE,
                SECRETS.VISIBILITY,
                USERS.USER_ID,
                USERS.USERNAME,
                USERS.DOMAIN,
                USERS.DISPLAY_NAME,
                USERS.USER_TYPE)
                .from(SECRETS)
                .leftJoin(USERS).on(SECRETS.OWNER_ID.eq(USERS.USER_ID));
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
