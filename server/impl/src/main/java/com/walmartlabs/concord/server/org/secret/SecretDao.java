package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.secret.*;
import com.walmartlabs.concord.server.api.org.secret.SecretEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretOwner;
import com.walmartlabs.concord.server.api.org.secret.SecretType;
import com.walmartlabs.concord.server.api.org.secret.SecretVisibility;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.SecretTeamAccess.SECRET_TEAM_ACCESS;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;

@Named
public class SecretDao extends AbstractDao {

    @Inject
    public SecretDao(Configuration cfg) {
        super(cfg);
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

    public UUID insert(UUID orgId, String name, UUID ownerId, SecretType type,
              SecretEncryptedByType encryptedBy, SecretStoreType storeType,
                       SecretVisibility visibility) {

        return txResult(tx -> insert(tx, orgId, name, ownerId, type, encryptedBy, storeType, visibility));
    }

    public UUID insert(DSLContext tx, UUID orgId, String name, UUID ownerId, SecretType type,
                       SecretEncryptedByType encryptedBy, SecretStoreType storeType,
                       SecretVisibility visibility) {

        return tx.insertInto(SECRETS)
                .columns(SECRETS.SECRET_NAME,
                        SECRETS.SECRET_TYPE,
                        SECRETS.ORG_ID,
                        SECRETS.OWNER_ID,
                        SECRETS.ENCRYPTED_BY,
                        SECRETS.STORE_TYPE,
                        SECRETS.VISIBILITY)
                .values(name, type.toString(), orgId, ownerId, encryptedBy.toString(), storeType.toString(), visibility.toString())
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

    public void update(DSLContext tx, UUID id, UUID orgId, String name, SecretType type, SecretEncryptedByType encryptedByType, SecretVisibility visibility, byte[] data) {
        int i = tx.update(SECRETS)
                .set(SECRETS.ORG_ID, orgId)
                .set(SECRETS.SECRET_NAME, name)
                .set(SECRETS.SECRET_TYPE, type.toString())
                .set(SECRETS.ENCRYPTED_BY, encryptedByType.toString())
                .set(SECRETS.SECRET_DATA, data)
                .where(SECRETS.SECRET_ID.eq(id))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public List<SecretEntry> list(UUID orgId, Field<?> sortField, boolean asc) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record10<UUID, String, UUID, String, UUID, String, String, String, String, String>> query = selectEntry(tx);

            if (orgId != null) {
                query.where(SECRETS.ORG_ID.eq(orgId));
            }

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(SecretDao::toEntry);
        }
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(SECRETS)
                .where(SECRETS.SECRET_ID.eq(id))
                .execute());
    }

    public boolean hasAccessLevel(UUID secretId, UUID userId, ResourceAccessLevel... levels) {
        try (DSLContext tx = DSL.using(cfg)) {
            return hasAccessLevel(tx, secretId, userId, levels);
        }
    }

    public boolean hasAccessLevel(DSLContext tx, UUID secretId, UUID userId, ResourceAccessLevel... levels) {
        SelectConditionStep<Record1<UUID>> teamIds = select(USER_TEAMS.TEAM_ID)
                .from(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(userId));

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

    private static SelectJoinStep<Record10<UUID, String, UUID, String, UUID, String, String, String, String, String>> selectEntry(DSLContext tx) {
        Field<String> orgName = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(SECRETS.ORG_ID)).asField();

        Field<String> ownerUsernameField = select(USERS.USERNAME)
                .from(USERS)
                .where(USERS.USER_ID.eq(SECRETS.OWNER_ID))
                .asField();

        return tx.select(SECRETS.SECRET_ID,
                SECRETS.SECRET_NAME,
                SECRETS.ORG_ID,
                orgName,
                SECRETS.OWNER_ID,
                ownerUsernameField,
                SECRETS.SECRET_TYPE,
                SECRETS.ENCRYPTED_BY,
                SECRETS.STORE_TYPE,
                SECRETS.VISIBILITY)
                .from(SECRETS);
    }

    private static SecretEntry toEntry(Record10<UUID, String, UUID, String, UUID, String, String, String, String, String> r) {
        return new SecretEntry(r.get(SECRETS.SECRET_ID),
                r.get(SECRETS.SECRET_NAME),
                r.get(SECRETS.ORG_ID),
                r.value4(),
                SecretType.valueOf(r.get(SECRETS.SECRET_TYPE)),
                SecretEncryptedByType.valueOf(r.get(SECRETS.ENCRYPTED_BY)),
                SecretStoreType.valueOf(r.get(SECRETS.STORE_TYPE)),
                SecretVisibility.valueOf(r.get(SECRETS.VISIBILITY)),
                toOwner(r.get(SECRETS.OWNER_ID), r.value6()));
    }

    private static SecretOwner toOwner(UUID id, String username) {
        if (id == null) {
            return null;
        }
        return new SecretOwner(id, username);
    }

    public static class SecretDataEntry extends SecretEntry {

        private final byte[] data;

        public SecretDataEntry(SecretEntry s, byte[] data) { //NOSONAR
            this(s.getId(), s.getName(), s.getOrgId(), s.getOrgName(), s.getType(), s.getEncryptedByType(),
                    s.getStoreType(), s.getVisibility(), s.getOwner(), data);
        }

        public SecretDataEntry(UUID id, String name, UUID orgId, String orgName , SecretType type,
                               SecretEncryptedByType encryptedByType, SecretStoreType storeType, SecretVisibility visibility,
                               SecretOwner owner, byte[] data) { //NOSONAR

            super(id, name, orgId, orgName, type, encryptedByType, storeType, visibility, owner);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }
}
