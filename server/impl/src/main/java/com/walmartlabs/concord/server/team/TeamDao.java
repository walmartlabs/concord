package com.walmartlabs.concord.server.team;

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.api.team.TeamUserEntry;
import com.walmartlabs.concord.server.api.team.TeamVisibility;
import com.walmartlabs.concord.server.jooq.tables.Teams;
import com.walmartlabs.concord.server.jooq.tables.records.TeamsRecord;
import com.walmartlabs.concord.server.jooq.tables.records.UserTeamsRecord;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.selectFrom;

@Named
public class TeamDao extends AbstractDao {

    public static final UUID DEFAULT_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Inject
    public TeamDao(Configuration cfg) {
        super(cfg);
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    public UUID getId(String name) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(TEAMS.TEAM_ID)
                    .from(TEAMS)
                    .where(TEAMS.TEAM_NAME.eq(name))
                    .fetchOne(TEAMS.TEAM_ID);
        }
    }

    public UUID insert(String name, String description, boolean isActive, TeamVisibility visibility) {
        return txResult(tx -> insert(tx, name, description, isActive, visibility));
    }

    public UUID insert(DSLContext tx, String name, String description, boolean isActive, TeamVisibility visibility) {
        return tx.insertInto(TEAMS)
                .columns(TEAMS.TEAM_NAME, TEAMS.DESCRIPTION, TEAMS.IS_ACTIVE, TEAMS.VISIBILITY)
                .values(name, description, isActive, visibility.toString())
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

    public List<TeamEntry> list(UUID currentUserId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, currentUserId);
        }
    }

    public List<TeamEntry> list(DSLContext tx, UUID currentUserId) {
        Teams t = TEAMS.as("t");

        Condition filterByTeamMember = exists(selectFrom(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(currentUserId)
                        .and(USER_TEAMS.TEAM_ID.eq(t.TEAM_ID))));

        return tx.selectFrom(t)
                .where(t.IS_ACTIVE.isTrue()
                        .and(t.VISIBILITY.eq(TeamVisibility.PUBLIC.toString())
                                .or(filterByTeamMember)))
                .orderBy(t.TEAM_NAME)
                .fetch(TeamDao::toEntry);
    }

    public List<TeamUserEntry> listUsers(UUID teamId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return listUsers(tx, teamId);
        }
    }

    public List<TeamUserEntry> listUsers(DSLContext tx, UUID teamId) {
        return tx.select(USERS.USER_ID, USERS.USERNAME, USER_TEAMS.TEAM_ROLE)
                .from(USER_TEAMS)
                .innerJoin(USERS).on(USERS.USER_ID.eq(USER_TEAMS.USER_ID))
                .where(USER_TEAMS.TEAM_ID.eq(teamId))
                .orderBy(USERS.USERNAME)
                .fetch((Record3<UUID, String, String> r) ->
                        new TeamUserEntry(r.get(USERS.USER_ID),
                                r.get(USERS.USERNAME),
                                TeamRole.valueOf(r.get(USER_TEAMS.TEAM_ROLE))));
    }

    public void addUser(UUID teamId, UUID userId, TeamRole role) {
        tx(tx -> addUsers(tx, teamId, userId, role));
    }

    public void addUsers(DSLContext tx, UUID teamId, UUID userId, TeamRole role) {
        tx.insertInto(USER_TEAMS)
                .columns(USER_TEAMS.TEAM_ID, USER_TEAMS.USER_ID, USER_TEAMS.TEAM_ROLE)
                .values(teamId, userId, role.toString())
                .execute();
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

    public boolean hasUser(UUID teamId, UUID userId, TeamRole... roles) {
        try (DSLContext tx = DSL.using(cfg)) {
            return hasUser(tx, teamId, userId, roles);
        }
    }

    public boolean hasUser(DSLContext tx, UUID teamId, UUID userId, TeamRole... roles) {
        SelectConditionStep<UserTeamsRecord> q = tx.selectFrom(USER_TEAMS)
                .where(USER_TEAMS.TEAM_ID.eq(teamId)
                        .and(USER_TEAMS.USER_ID.eq(userId)));

        if (roles != null && roles.length != 0) {
            q.and(USER_TEAMS.TEAM_ROLE.in(Utils.toString(roles)));
        }

        return tx.fetchExists(q);
    }

    private static TeamEntry toEntry(TeamsRecord r) {
        return new TeamEntry(r.getTeamId(), r.getTeamName(), r.getDescription(),
                r.getIsActive(), TeamVisibility.valueOf(r.getVisibility()));
    }
}
