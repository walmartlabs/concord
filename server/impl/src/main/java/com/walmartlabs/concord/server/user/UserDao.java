package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.user.UserEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserPermissions.USER_PERMISSIONS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectDistinct;
import static org.jooq.impl.DSL.selectFrom;

@Named
public class UserDao extends AbstractDao {

    @Inject
    public UserDao(Configuration cfg) {
        super(cfg);
    }

    public UUID insert(String username, Set<String> permissions, boolean admin) {
        return txResult(tx -> insert(tx, username, permissions, admin));
    }

    public UUID insert(DSLContext tx, String username, Set<String> permissions, boolean admin) {
        UUID id = tx.insertInto(USERS)
                .columns(USERS.USERNAME, USERS.IS_ADMIN)
                .values(username, admin)
                .returning(USERS.USER_ID)
                .fetchOne().getUserId();

        insertPermissions(tx, id, permissions);

        return id;
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(USERS)
                .where(USERS.USER_ID.eq(id))
                .execute());
    }

    public void update(UUID id, Set<String> permissions, Boolean admin) {
        tx(tx -> {
            if (admin != null) {
                tx.update(USERS)
                        .set(USERS.IS_ADMIN, admin)
                        .where(USERS.USER_ID.eq(id))
                        .execute();
            }

            deletePermissions(tx, id);
            insertPermissions(tx, id, permissions);
        });
    }

    public UserEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            Record3<UUID, String, Boolean> r = tx.select(USERS.USER_ID, USERS.USERNAME, USERS.IS_ADMIN)
                    .from(USERS)
                    .where(USERS.USER_ID.eq(id))
                    .fetchOne();

            if (r == null) {
                return null;
            }

            // TODO join?
            List<String> perms = tx.select(USER_PERMISSIONS.PERMISSION)
                    .from(USER_PERMISSIONS)
                    .where(USER_PERMISSIONS.USER_ID.eq(id))
                    .fetchInto(String.class);

            Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME)
                    .from(ORGANIZATIONS)
                    .where(ORGANIZATIONS.ORG_ID.eq(TEAMS.ORG_ID)).asField();

            SelectConditionStep<Record1<UUID>> teamIds = select(USER_TEAMS.TEAM_ID)
                    .from(USER_TEAMS)
                    .where(USER_TEAMS.USER_ID.eq(id));

            List<OrganizationEntry> orgs = tx.selectDistinct(TEAMS.ORG_ID, orgNameField)
                    .from(TEAMS)
                    .where(TEAMS.TEAM_ID.in(teamIds))
                    .fetch(e -> new OrganizationEntry(e.value1(), e.value2()));

            return new UserEntry(r.get(USERS.USER_ID),
                    r.get(USERS.USERNAME),
                    new HashSet<>(perms),
                    new HashSet<>(orgs),
                    r.get(USERS.IS_ADMIN));
        }
    }

    public UUID getId(String username) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(USERS.USER_ID)
                    .from(USERS)
                    .where(USERS.USERNAME.eq(username))
                    .fetchOne(USERS.USER_ID);
        }
    }

    public boolean exists(String username) {
        try (DSLContext tx = DSL.using(cfg)) {
            int cnt = tx.fetchCount(tx.selectFrom(USERS)
                    .where(USERS.USERNAME.eq(username)));

            return cnt > 0;
        }
    }

    public boolean existsById(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            int cnt = tx.fetchCount(tx.selectFrom(USERS)
                    .where(USERS.USER_ID.eq(id)));

            return cnt > 0;
        }
    }

    public boolean isInOrganization(UUID userId, UUID orgId) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                    .from(TEAMS)
                    .where(TEAMS.ORG_ID.eq(orgId));

            return tx.fetchExists(selectFrom(USER_TEAMS)
                    .where(USER_TEAMS.USER_ID.eq(userId)
                            .and(USER_TEAMS.TEAM_ID.in(teamIds))));
        }
    }

    public Set<UUID> getOrgIds(UUID userId) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<Record1<UUID>> teamIds = select(USER_TEAMS.TEAM_ID)
                    .from(USER_TEAMS)
                    .where(USER_TEAMS.USER_ID.eq(userId));

            return tx.selectDistinct(TEAMS.ORG_ID)
                    .from(TEAMS)
                    .where(TEAMS.TEAM_ID.in(teamIds))
                    .fetchSet(TEAMS.ORG_ID);
        }
    }

    private static void insertPermissions(DSLContext tx, UUID userId, Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return;
        }

        BatchBindStep b = tx.batch(tx.insertInto(USER_PERMISSIONS)
                .columns(USER_PERMISSIONS.USER_ID, USER_PERMISSIONS.PERMISSION)
                .values((UUID) null, null));

        for (String p : permissions) {
            b.bind(userId, p);
        }

        b.execute();
    }

    private static void deletePermissions(DSLContext tx, UUID userId) {
        tx.deleteFrom(USER_PERMISSIONS).where(USER_PERMISSIONS.USER_ID.eq(userId)).execute();
    }
}
