package com.walmartlabs.concord.server.org.secret;

import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.secret.SecretEntry;
import com.walmartlabs.concord.server.api.org.secret.SecretOwner;
import com.walmartlabs.concord.server.api.org.secret.SecretType;
import com.walmartlabs.concord.server.api.org.secret.SecretVisibility;
import com.walmartlabs.concord.server.org.OrganizationManager;
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
                       SecretStoreType storeType, SecretVisibility visibility, byte[] data) {

        return txResult(tx -> insert(tx, orgId, name, ownerId, type, storeType, visibility, data));
    }

    public UUID insert(DSLContext tx, UUID orgId, String name, UUID ownerId, SecretType type,
                       SecretStoreType storeType, SecretVisibility visibility, byte[] data) {

        return tx.insertInto(SECRETS)
                .columns(SECRETS.SECRET_NAME,
                        SECRETS.SECRET_TYPE,
                        SECRETS.ORG_ID,
                        SECRETS.OWNER_ID,
                        SECRETS.SECRET_STORE_TYPE,
                        SECRETS.VISIBILITY,
                        SECRETS.SECRET_DATA)
                .values(name, type.toString(), orgId, ownerId, storeType.toString(), visibility.toString(), data)
                .returning(SECRETS.SECRET_ID)
                .fetchOne()
                .getSecretId();
    }

    public void update(DSLContext tx, UUID id, UUID orgId, String name, SecretType type, SecretStoreType storeType, SecretVisibility visibility, byte[] data) {
        int i = tx.update(SECRETS)
                .set(SECRETS.ORG_ID, orgId)
                .set(SECRETS.SECRET_NAME, name)
                .set(SECRETS.SECRET_TYPE, type.toString())
                .set(SECRETS.SECRET_STORE_TYPE, storeType.toString())
                .set(SECRETS.SECRET_DATA, data)
                .where(SECRETS.SECRET_ID.eq(id))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows updated: " + i);
        }
    }

    public SecretDataEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectSecretDataEntry(tx)
                    .where(SECRETS.SECRET_ID.eq(id))
                    .fetchOne(SecretDao::toDataEntry);
        }
    }

    public SecretDataEntry getByName(UUID orgId, String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectSecretDataEntry(tx)
                    .where(SECRETS.ORG_ID.eq(orgId)
                            .and(SECRETS.SECRET_NAME.eq(name)))
                    .fetchOne(SecretDao::toDataEntry);
        }
    }

    public List<SecretEntry> list(UUID orgId, Field<?> sortField, boolean asc) {
        Field<String> orgName = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(SECRETS.ORG_ID)).asField();

        Field<String> ownerUsernameField = select(USERS.USERNAME)
                .from(USERS)
                .where(USERS.USER_ID.eq(SECRETS.OWNER_ID))
                .asField();

        try (DSLContext tx = DSL.using(cfg)) {
            SelectJoinStep<Record9<UUID, String, UUID, String, UUID, String, String, String, String>> query = tx
                    .select(SECRETS.SECRET_ID,
                            SECRETS.SECRET_NAME,
                            SECRETS.ORG_ID,
                            orgName,
                            SECRETS.OWNER_ID,
                            ownerUsernameField,
                            SECRETS.SECRET_TYPE,
                            SECRETS.SECRET_STORE_TYPE,
                            SECRETS.VISIBILITY)
                    .from(SECRETS);

            if (orgId != null) {
                query.where(SECRETS.ORG_ID.eq(orgId));
            }

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            return query.fetch(r -> new SecretEntry(r.get(SECRETS.SECRET_ID),
                    r.get(SECRETS.SECRET_NAME),
                    r.get(SECRETS.ORG_ID),
                    r.get(orgName),
                    SecretType.valueOf(r.get(SECRETS.SECRET_TYPE)),
                    SecretStoreType.valueOf(r.get(SECRETS.SECRET_STORE_TYPE)),
                    SecretVisibility.valueOf(r.get(SECRETS.VISIBILITY)),
                    toOwner(r.get(SECRETS.OWNER_ID), r.get(ownerUsernameField))));
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

    private static SelectJoinStep<Record10<UUID, String, UUID, String, UUID, String, String, String, String, byte[]>> selectSecretDataEntry(DSLContext tx) {
        Field<String> orgName = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(SECRETS.ORG_ID)).asField();

        Field<String> ownerUsernameField = select(USERS.USERNAME)
                .from(USERS)
                .where(USERS.USER_ID.eq(SECRETS.OWNER_ID))
                .asField();

        return tx.select(
                SECRETS.SECRET_ID,
                SECRETS.SECRET_NAME,
                SECRETS.ORG_ID,
                orgName,
                SECRETS.OWNER_ID,
                ownerUsernameField,
                SECRETS.SECRET_TYPE,
                SECRETS.SECRET_STORE_TYPE,
                SECRETS.VISIBILITY,
                SECRETS.SECRET_DATA)
                .from(SECRETS);
    }

    private static SecretDataEntry toDataEntry(Record10<UUID, String, UUID, String, UUID, String, String, String, String, byte[]> r) {
        return new SecretDataEntry(r.get(SECRETS.SECRET_ID),
                r.get(SECRETS.SECRET_NAME),
                r.get(SECRETS.ORG_ID),
                r.value4(),
                SecretType.valueOf(r.get(SECRETS.SECRET_TYPE)),
                SecretStoreType.valueOf(r.get(SECRETS.SECRET_STORE_TYPE)),
                SecretVisibility.valueOf(r.get(SECRETS.VISIBILITY)),
                toOwner(r.get(SECRETS.OWNER_ID), r.value6()),
                r.get(SECRETS.SECRET_DATA));
    }

    private static SecretOwner toOwner(UUID id, String username) {
        if (id == null) {
            return null;
        }
        return new SecretOwner(id, username);
    }

    public static class SecretDataEntry extends SecretEntry {

        private final byte[] data;

        public SecretDataEntry(SecretDataEntry s, byte[] data) {
            this(s.getId(), s.getName(), s.getTeamId(), s.getTeamName(), s.getType(), s.getStoreType(),
                    s.getVisibility(), s.getOwner(), data);
        }

        public SecretDataEntry(UUID id, String name, UUID orgId, String teamName, SecretType type,
                               SecretStoreType storeType, SecretVisibility visibility, SecretOwner owner, byte[] data) {
            super(id, name, orgId, teamName, type, storeType, visibility, owner);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }
}
