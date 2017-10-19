package com.walmartlabs.concord.server.team;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.team.TeamUserEntry;
import com.walmartlabs.concord.server.jooq.tables.records.TeamsRecord;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;

@Named
public class TeamDao extends AbstractDao {

    public static final UUID DEFAULT_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Inject
    public TeamDao(Configuration cfg) {
        super(cfg);
    }

    public UUID getId(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(TEAMS.TEAM_ID)
                    .from(TEAMS)
                    .where(TEAMS.TEAM_NAME.eq(name))
                    .fetchOne(TEAMS.TEAM_ID);
        }
    }

    public UUID insert(String name, String description, boolean isActive) {
        return txResult(tx -> insert(tx, name, description, isActive));
    }

    public UUID insert(DSLContext tx, String name, String description, boolean isActive) {
        return tx.insertInto(TEAMS)
                .columns(TEAMS.TEAM_NAME, TEAMS.DESCRIPTION, TEAMS.IS_ACTIVE)
                .values(name, description, isActive)
                .returning(TEAMS.TEAM_ID)
                .fetchOne()
                .getTeamId();
    }

    public void update(UUID id, String name, String description) {
        tx(tx -> update(tx, id, name, description));
    }

    public void update(DSLContext tx, UUID id, String name, String description) {
        tx.update(TEAMS)
                .set(TEAMS.TEAM_NAME, name)
                .set(TEAMS.DESCRIPTION, description)
                .where(TEAMS.TEAM_ID.eq(id))
                .execute();
    }

    public TeamEntry get(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, id);
        }
    }

    public TeamEntry get(DSLContext tx, UUID id) {
        return tx.selectFrom(TEAMS)
                .where(TEAMS.TEAM_ID.eq(id))
                .fetchOne(TeamDao::toEntry);
    }

    public TeamEntry getByName(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return getByName(tx, name);
        }
    }

    public TeamEntry getByName(DSLContext tx, String name) {
        return tx.selectFrom(TEAMS)
                .where(TEAMS.TEAM_NAME.eq(name))
                .fetchOne(TeamDao::toEntry);
    }

    public List<TeamEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx);
        }
    }

    public List<TeamEntry> list(DSLContext tx) {
        return tx.selectFrom(TEAMS)
                .orderBy(TEAMS.TEAM_NAME)
                .fetch(TeamDao::toEntry);
    }

    public List<TeamUserEntry> listUsers(UUID teamId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return listUsers(tx, teamId);
        }
    }

    public List<TeamUserEntry> listUsers(DSLContext tx, UUID teamId) {
        return tx.select(USERS.USER_ID, USERS.USERNAME)
                .from(USER_TEAMS)
                .innerJoin(USERS).on(USERS.USER_ID.eq(USER_TEAMS.USER_ID))
                .where(USER_TEAMS.TEAM_ID.eq(teamId))
                .orderBy(USERS.USERNAME)
                .fetch((Record2<UUID, String> r) ->
                        new TeamUserEntry(r.get(USERS.USER_ID), r.get(USERS.USERNAME)));
    }

    public void addUsers(UUID teamId, Collection<UUID> userIds) {
        tx(tx -> addUsers(tx, teamId, userIds));
    }

    public void addUsers(DSLContext tx, UUID teamId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        BatchBindStep b = tx.batch(tx.insertInto(USER_TEAMS)
                .columns(USER_TEAMS.USER_ID, USER_TEAMS.TEAM_ID)
                .values((UUID) null, null));

        for (UUID userId : userIds) {
            b.bind(userId, teamId);
        }

        b.execute();
    }

    public void removeUsers(UUID teamId, Collection<UUID> userIds) {
        tx(tx -> removeUsers(tx, teamId, userIds));
    }

    public void removeUsers(DSLContext tx, UUID teamId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        tx.deleteFrom(USER_TEAMS)
                .where(USER_TEAMS.TEAM_ID.eq(teamId)
                        .and(USER_TEAMS.USER_ID.in(userIds)))
                .execute();
    }

    private static TeamEntry toEntry(TeamsRecord r) {
        return new TeamEntry(r.getTeamId(), r.getTeamName(), r.getDescription(), r.getIsActive());
    }
}
