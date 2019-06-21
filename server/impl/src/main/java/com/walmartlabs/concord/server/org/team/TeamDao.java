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
import com.walmartlabs.concord.server.jooq.tables.TeamLdapGroups;
import com.walmartlabs.concord.server.jooq.tables.records.TeamsRecord;
import com.walmartlabs.concord.server.jooq.tables.records.VUserTeamsRecord;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.TEAM_LDAP_GROUPS;
import static com.walmartlabs.concord.server.jooq.Tables.V_USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;

@Named
public class TeamDao extends AbstractDao {

    @Inject
    public TeamDao(@MainDB Configuration cfg) {
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
            return tx.select(TEAMS.TEAM_ID)
                    .from(TEAMS)
                    .where(TEAMS.ORG_ID.eq(orgId).and(TEAMS.TEAM_NAME.eq(name)))
                    .fetchOne(TEAMS.TEAM_ID);
        }
    }

    public UUID getOrgId(UUID teamId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(TEAMS.ORG_ID)
                    .from(TEAMS)
                    .where(TEAMS.TEAM_ID.eq(teamId))
                    .fetchOne(TEAMS.ORG_ID);
        }
    }

    public UUID insert(UUID orgId, String name, String description) {
        return txResult(tx -> insert(tx, orgId, name, description));
    }

    public UUID insert(DSLContext tx, UUID orgId, String name, String description) {
        return tx.insertInto(TEAMS)
                .columns(TEAMS.ORG_ID, TEAMS.TEAM_NAME, TEAMS.DESCRIPTION)
                .values(orgId, name, description)
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
        try (DSLContext tx = DSL.using(cfg)) {
            return get(tx, id);
        }
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
        try (DSLContext tx = DSL.using(cfg)) {
            return getByName(tx, orgId, name);
        }
    }

    public TeamEntry getByName(DSLContext tx, UUID orgId, String name) {
        return selectTeams(tx)
                .where(TEAMS.ORG_ID.eq(orgId).and(TEAMS.TEAM_NAME.eq(name)))
                .fetchOne(TeamDao::toEntry);
    }

    public List<TeamEntry> list(UUID orgId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, orgId);
        }
    }

    public List<TeamEntry> list(DSLContext tx, UUID orgId) {
        return selectTeams(tx)
                .where(TEAMS.ORG_ID.eq(orgId))
                .orderBy(TEAMS.TEAM_NAME)
                .fetch(TeamDao::toEntry);
    }

    public List<TeamUserEntry> listUsers(UUID teamId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return listUsers(tx, teamId);
        }
    }

    public List<TeamUserEntry> listUsers(DSLContext tx, UUID teamId) {
        return tx.select(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_TYPE, USER_TEAMS.TEAM_ROLE)
                .from(USER_TEAMS)
                .innerJoin(USERS).on(USERS.USER_ID.eq(USER_TEAMS.USER_ID))
                .where(USER_TEAMS.TEAM_ID.eq(teamId))
                .orderBy(USERS.USERNAME)
                .fetch((Record6<UUID, String, String, String, String, String> r) ->
                        new TeamUserEntry(r.value1(),
                                r.value2(),
                                r.value3(),
                                r.value4(),
                                UserType.valueOf(r.value5()),
                                TeamRole.valueOf(r.value6())));
    }

    public List<TeamLdapGroupEntry> listLdapGroups(UUID teamId) {
        TeamLdapGroups t = TEAM_LDAP_GROUPS.as("t");
        return txResult(tx -> tx.select(t.LDAP_GROUP, t.TEAM_ROLE)
                .from(t)
                .where(t.TEAM_ID.eq(teamId))
                .orderBy(t.LDAP_GROUP)
                .fetch(r -> TeamLdapGroupEntry.builder()
                        .group(r.value1())
                        .role(TeamRole.valueOf(r.value2()))
                        .build()));
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
        tx.insertInto(TEAM_LDAP_GROUPS)
                .columns(TEAM_LDAP_GROUPS.TEAM_ID, TEAM_LDAP_GROUPS.LDAP_GROUP, TEAM_LDAP_GROUPS.TEAM_ROLE)
                .values(teamId, ldapGroup, role.toString())
                .onConflict(TEAM_LDAP_GROUPS.TEAM_ID, TEAM_LDAP_GROUPS.LDAP_GROUP)
                .doUpdate().set(TEAM_LDAP_GROUPS.TEAM_ROLE, role.toString())
                .execute();
    }

    public void removeLdapGroups(DSLContext tx, UUID teamId) {
        tx.deleteFrom(TEAM_LDAP_GROUPS)
                .where(TEAM_LDAP_GROUPS.TEAM_ID.eq(teamId))
                .execute();
    }

    public boolean isInAnyTeam(UUID orgId, UUID userId, TeamRole... roles) {
        try (DSLContext tx = DSL.using(cfg)) {
            return isInAnyTeam(tx, orgId, userId, roles);
        }
    }

    public boolean isInAnyTeam(DSLContext tx, UUID orgId, UUID userId, TeamRole... roles) {
        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID).from(TEAMS).where(TEAMS.ORG_ID.eq(orgId));
        return tx.fetchExists(selectFrom(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(userId)
                        .and(V_USER_TEAMS.TEAM_ID.in(teamIds))
                        .and(V_USER_TEAMS.TEAM_ROLE.in(Utils.toString(roles)))));
    }

    public boolean hasUser(UUID teamId, UUID userId, TeamRole... roles) {
        try (DSLContext tx = DSL.using(cfg)) {
            return hasUser(tx, teamId, userId, roles);
        }
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
