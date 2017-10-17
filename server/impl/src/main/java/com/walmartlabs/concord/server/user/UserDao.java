package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.user.UserEntry;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserPermissions.USER_PERMISSIONS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;

@Named
public class UserDao extends AbstractDao {

    @Inject
    public UserDao(Configuration cfg) {
        super(cfg);
    }

    public UUID insert(String username, Set<String> permissions) {
        return txResult(tx -> insert(tx, username, permissions));
    }

    public UUID insert(DSLContext tx, String username, Set<String> permissions) {
        UUID id = tx.insertInto(USERS)
                .columns(USERS.USERNAME)
                .values(username)
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

    public void update(UUID id, Set<String> permissions) {
        tx(tx -> {
            deletePermissions(tx, id);
            insertPermissions(tx, id, permissions);
        });
    }

    public UserEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            Record2<UUID, String> r = tx.select(USERS.USER_ID, USERS.USERNAME)
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

            List<TeamEntry> teams = tx.select(TEAMS.TEAM_ID, TEAMS.TEAM_NAME, TEAMS.IS_ACTIVE)
                    .from(USER_TEAMS)
                    .join(TEAMS).on(TEAMS.TEAM_ID.eq(USER_TEAMS.TEAM_ID))
                    .where(USER_TEAMS.USER_ID.eq(id).and(TEAMS.IS_ACTIVE.isTrue()))
                    .fetch(e -> new TeamEntry(e.value1(), e.value2(), null, e.value3()));

            return new UserEntry(r.get(USERS.USER_ID), r.get(USERS.USERNAME),
                    new HashSet<>(perms), new HashSet<>(teams));
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
