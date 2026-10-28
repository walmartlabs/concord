package com.walmartlabs.concord.server.org.team;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.jooq.tables.records.TeamsRecord;
import com.walmartlabs.concord.server.jooq.tables.records.VUserTeamsRecord;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;

import javax.inject.Inject;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static java.util.Objects.requireNonNull;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;

public class TeamDao extends AbstractDao {

    private final UuidGenerator uuidGenerator;

    @Inject
    public TeamDao(@MainDB Configuration cfg, UuidGenerator uuidGenerator) {
        super(cfg);
        this.uuidGenerator = requireNonNull(uuidGenerator);
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
        return dsl().select(TEAMS.TEAM_ID)
                .from(TEAMS)
                .where(TEAMS.ORG_ID.eq(orgId).and(TEAMS.TEAM_NAME.eq(name)))
                .fetchOne(TEAMS.TEAM_ID);
    }

    public UUID getOrgId(UUID teamId) {
        return dsl().select(TEAMS.ORG_ID)
                .from(TEAMS)
                .where(TEAMS.TEAM_ID.eq(teamId))
                .fetchOne(TEAMS.ORG_ID);
    }

    public UUID insert(UUID orgId, String name, String description) {
        return txResult(tx -> insert(tx, orgId, name, description));
    }

    public UUID insert(DSLContext tx, UUID orgId, String name, String description) {
        UUID teamId = uuidGenerator.generate();
        return tx.insertInto(TEAMS)
                .columns(TEAMS.TEAM_ID, TEAMS.ORG_ID, TEAMS.TEAM_NAME, TEAMS.DESCRIPTION)
                .values(teamId, orgId, name, description)
                .returning(TEAMS.TEAM_ID)
                .fetchOne()
                .getTeamId();
    }

    public void update(UUID id, String name, String description) {
        tx(tx -> update(tx, id, name, description));
    }

    public void update(DSLContext tx, UUID id, String name, String description) {
        UpdateSetFirstStep<TeamsRecord> q = tx.update(TEAMS);

        if (description != null) {
            q.set(TEAMS.DESCRIPTION, description);
        }

        q.set(TEAMS.TEAM_NAME, name)
                .where(TEAMS.TEAM_ID.eq(id))
                .execute();
    }

    public void delete(UUID id) {
        tx(tx -> delete(tx, id));
    }

    public void delete(DSLContext tx, UUID id) {
        tx.deleteFrom(TEAMS)
                .where(TEAMS.TEAM_ID.eq(id))
                .execute();
    }

    public TeamEntry get(UUID id) {
        return get(dsl(), id);
    }

    private static SelectJoinStep<Record5<UUID, UUID, String, String, String>> selectTeams(DSLContext tx) {
        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(TEAMS.ORG_ID)).asField();

        return tx.select(TEAMS.TEAM_ID,
                TEAMS.ORG_ID,
                orgNameField,
                TEAMS.TEAM_NAME,
                TEAMS.DESCRIPTION)
                .from(TEAMS);
    }

    public TeamEntry get(DSLContext tx, UUID id) {
        return selectTeams(tx)
                .where(TEAMS.TEAM_ID.eq(id))
                .fetchOne(TeamDao::toEntry);
    }

    public TeamEntry getByName(UUID orgId, String name) {
        return getByName(dsl(), orgId, name);
    }

    public TeamEntry getByName(DSLContext tx, UUID orgId, String name) {
        return selectTeams(tx)
                .where(TEAMS.ORG_ID.eq(orgId).and(TEAMS.TEAM_NAME.eq(name)))
                .fetchOne(TeamDao::toEntry);
    }

    public List<TeamEntry> list(UUID orgId) {
        return list(dsl(), orgId);
    }

    public List<TeamEntry> list(DSLContext tx, UUID orgId) {
        return selectTeams(tx)
                .where(TEAMS.ORG_ID.eq(orgId))
                .orderBy(TEAMS.TEAM_NAME)
                .fetch(TeamDao::toEntry);
    }

    public List<TeamUserEntry> listUsers(UUID teamId) {
        DSLContext tx = dsl();

        List<TeamUserEntry> l = new LinkedList<>();
        l.addAll(listMembers(tx, teamId));
        l.addAll(listLdapGroupMembers(tx, teamId));

        return l;
    }

    public List<TeamUserEntry> listMembers(DSLContext tx, UUID teamId) {
        return tx.selectDistinct(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_TYPE, USER_TEAMS.TEAM_ROLE)
                .from(USER_TEAMS)
                .innerJoin(USERS).on(USERS.USER_ID.eq(USER_TEAMS.USER_ID))
                .where(USER_TEAMS.TEAM_ID.eq(teamId))
                .orderBy(USERS.USERNAME)
                .fetch((Record6<UUID, String, String, String, String, String> r) ->
                        new TeamUserEntry(r.value1(),
                                r.value2(),
                                r.value3(),
                                r.value4(),
                                UserType.fromNullable(r.value5()),
                                TeamRole.valueOf(r.value6()),
                                TeamMemberType.SINGLE,
                                null));
    }

    public List<TeamUserEntry> listLdapGroupMembers(DSLContext tx, UUID teamId) {
        return tx.fetch("SELECT u.user_id, u.username, u.domain, u.display_name, u.user_type, m.team_role, g.group_id " +
                        "FROM users u JOIN v_effective_source_groups g ON g.user_id = u.user_id " +
                        "JOIN team_source_groups m ON m.source_id = g.source_id AND m.group_id = g.group_id " +
                        "WHERE m.team_id = ? AND g.source_id = 'ldap' ORDER BY u.username", teamId)
                .map(r -> new TeamUserEntry(r.get(0, UUID.class), r.get(1, String.class),
                        r.get(2, String.class), r.get(3, String.class),
                        UserType.fromNullable(r.get(4, String.class)), TeamRole.valueOf(r.get(5, String.class)),
                        TeamMemberType.LDAP_GROUP, r.get(6, String.class)));
    }

    public List<TeamLdapGroupEntry> listLdapGroups(UUID teamId) {
        return listGroups(teamId, "ldap").stream()
                .<TeamLdapGroupEntry>map(g -> TeamLdapGroupEntry.builder().group(g.groupId()).role(g.role()).build())
                .toList();
    }

    public List<TeamSourceGroupEntry> listGroups(UUID teamId, String sourceId) {
        return dsl().fetch("SELECT group_id, team_role FROM team_source_groups WHERE team_id = ? AND source_id = ? ORDER BY group_id",
                teamId, sourceId).map(r -> new TeamSourceGroupEntry(r.get(0, String.class), TeamRole.valueOf(r.get(1, String.class))));
    }

    public void upsertUser(UUID teamId, UUID userId, TeamRole role) {
        tx(tx -> upsertUser(tx, teamId, userId, role));
    }

    public void upsertUser(DSLContext tx, UUID teamId, UUID userId, TeamRole role) {
        tx.insertInto(USER_TEAMS)
                .columns(USER_TEAMS.TEAM_ID, USER_TEAMS.USER_ID, USER_TEAMS.TEAM_ROLE)
                .values(teamId, userId, role.toString())
                .onConflict(USER_TEAMS.TEAM_ID, USER_TEAMS.USER_ID)
                .doUpdate().set(USER_TEAMS.TEAM_ROLE, role.toString())
                .execute();
    }

    public void removeUsers(DSLContext tx, UUID teamId) {
        tx.deleteFrom(USER_TEAMS)
                .where(USER_TEAMS.TEAM_ID.eq(teamId))
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

    public void upsertLdapGroup(DSLContext tx, UUID teamId, String ldapGroup, TeamRole role) {
        upsertGroup(tx, teamId, "ldap", ldapGroup, role);
    }

    public void removeLdapGroups(DSLContext tx, UUID teamId) {
        removeGroups(tx, teamId, "ldap");
    }

    public void upsertGroup(DSLContext tx, UUID teamId, String sourceId, String groupId, TeamRole role) {
        tx.execute("INSERT INTO team_source_groups (team_id, source_id, group_id, team_role) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT (team_id, source_id, group_id) DO UPDATE SET team_role = EXCLUDED.team_role",
                teamId, sourceId, groupId, role.toString());
    }

    public void removeGroups(DSLContext tx, UUID teamId, String sourceId) {
        tx.execute("DELETE FROM team_source_groups WHERE team_id = ? AND source_id = ?", teamId, sourceId);
    }

    public boolean isInAnyTeam(UUID orgId, UUID userId, TeamRole... roles) {
        return isInAnyTeam(dsl(), orgId, userId, roles);
    }

    public boolean isInAnyTeam(DSLContext tx, UUID orgId, UUID userId, TeamRole... roles) {
        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID).from(TEAMS).where(TEAMS.ORG_ID.eq(orgId));
        return tx.fetchExists(selectFrom(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(userId)
                        .and(V_USER_TEAMS.TEAM_ID.in(teamIds))
                        .and(V_USER_TEAMS.TEAM_ROLE.in(Utils.toString(roles)))));
    }

    public boolean hasUser(UUID teamId, UUID userId, TeamRole... roles) {
        return hasUser(dsl(), teamId, userId, roles);
    }

    public boolean hasUser(DSLContext tx, UUID teamId, UUID userId, TeamRole... roles) {
        SelectConditionStep<VUserTeamsRecord> q = tx.selectFrom(V_USER_TEAMS)
                .where(V_USER_TEAMS.TEAM_ID.eq(teamId)
                        .and(V_USER_TEAMS.USER_ID.eq(userId)));

        if (roles != null && roles.length != 0) {
            q.and(V_USER_TEAMS.TEAM_ROLE.in(Utils.toString(roles)));
        }

        return tx.fetchExists(q);
    }

    private static TeamEntry toEntry(Record5<UUID, UUID, String, String, String> r) {
        return new TeamEntry(r.value1(), r.value2(), r.value3(), r.value4(), r.value5());
    }
}
