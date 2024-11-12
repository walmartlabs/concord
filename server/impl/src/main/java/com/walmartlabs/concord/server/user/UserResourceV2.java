package com.walmartlabs.concord.server.user;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.jooq.tables.records.UsersRecord;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapGroupSearchResult;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Roles.ROLES;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserRoles.USER_ROLES;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

@Named
public class UserDao extends AbstractDao {

    @Inject
    public UserDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    @Override
    protected <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    /**
     * Inserts a new record or updates an existing one.
     * Note that {@code username} and {@code domain} are case-insensitive and forced to lower case.
     */
    public UUID insertOrUpdate(String username, String domain, String displayName, String email, UserType type, Set<String> roles) {
        return txResult(tx -> {
            UUID userId = tx.insertInto(USERS)
                    .columns(USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.USER_TYPE)
                    .values(toLowerCase(username), toLowerCase(domain), displayName, email, type.toString())
                    .onConflict(lower(USERS.USERNAME), lower(USERS.DOMAIN), field(USERS.USER_TYPE))
                    .doUpdate()
                    .set(USERS.DISPLAY_NAME, displayName)
                    .set(USERS.USER_EMAIL, email)
                    .returning(USERS.USER_ID)
                    .fetchOne()
                    .getUserId();

            if (roles != null) {
                updateRoles(tx, userId, roles);
            }

            return userId;
        });
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(USERS)
                .where(USERS.USER_ID.eq(id))
                .execute());
    }

    public void enable(UUID id) {
        tx(tx -> tx.update(USERS)
                .set(USERS.IS_DISABLED, inline(false))
                .set(USERS.IS_PERMANENTLY_DISABLED, inline(false))
                .setNull(USERS.DISABLED_DATE)
                .where(USERS.USER_ID.eq(id))
                .execute());
    }

    public void disable(UUID id, boolean permanent) {
        tx(tx -> tx.update(USERS)
                .set(USERS.IS_DISABLED, inline(true))
                .set(Tables.USERS.IS_PERMANENTLY_DISABLED, permanent)
                .set(USERS.DISABLED_DATE, currentOffsetDateTime())
                .where(USERS.USER_ID.eq(id))
                .execute());
    }

    public UserEntry update(UUID id, String displayName, String email, UserType userType, boolean isDisabled, Set<String> roles) {
        tx(tx -> {
            UpdateSetMoreStep<UsersRecord> q = tx.update(USERS)
                    .set(USERS.IS_DISABLED, isDisabled);

            if (isDisabled) {
                q.set(USERS.DISABLED_DATE, currentOffsetDateTime());
            } else {
                q.setNull(USERS.DISABLED_DATE);
            }

            if (userType != null) {
                q.set(USERS.USER_TYPE, userType.name());
            }

            if (displayName != null) {
                q.set(USERS.DISPLAY_NAME, displayName);
            }

            if (email != null) {
                q.set(USERS.USER_EMAIL, email);
            }

            q.where(USERS.USER_ID.eq(id))
                    .execute();

            if (roles != null) {
                updateRoles(tx, id, roles);
            }
        });

        return get(id);
    }

    // TODO add "include" option
    public UserEntry get(UUID id) {
        DSLContext tx = dsl();

        Record9<UUID, String, String, String, String, String, Boolean, Boolean, OffsetDateTime> r =
                tx.select(USERS.USER_ID, USERS.USER_TYPE, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.IS_DISABLED, USERS.IS_PERMANENTLY_DISABLED, USERS.DISABLED_DATE)
                        .from(USERS)
                        .where(USERS.USER_ID.eq(id))
                        .fetchOne();

        if (r == null) {
            return null;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();

        return getUserInfo(tx, r);
    }

    /**
     * Returns the ID of the specified user.
     * Note that {@code username} and {@code domain} are case-insensitive and forced to lower case.
     */
    public UUID getId(String username, String domain, UserType type) {
        SelectConditionStep<Record1<UUID>> q = dsl().select(USERS.USER_ID)
                .from(USERS)
                .where(USERS.USERNAME.eq(toLowerCase(username)));

        if (domain != null) {
            q.and(USERS.DOMAIN.eq(toLowerCase(domain)));
        }

        if (type != null) {
            q.and(USERS.USER_TYPE.eq(type.toString()));
        }

        List<UUID> result = q.fetch(USERS.USER_ID);
        if (result.isEmpty()) {
            return null;
        } else if (result.size() > 1) {
            throw new IllegalArgumentException("Non unique results found for username: '" + username + "', domain: '" + domain + "', type: " + type +
                    ". Note that user and domain names are case-insensitive.");
        }

        return result.get(0);
    }

    public String getUsername(UUID id) {
        return dsl().select(USERS.USERNAME).from(USERS)
                .where(USERS.USER_ID.eq(id))
                .fetchOne(USERS.USERNAME);
    }

    public boolean existsById(UUID id) {
        int cnt = dsl().fetchCount(selectFrom(USERS)
                .where(USERS.USER_ID.eq(id)));

        return cnt > 0;
    }

    public boolean isInOrganization(UUID userId, UUID orgId) {
        return isInOrganization(dsl(), userId, orgId);
    }

    public boolean isInOrganization(DSLContext tx, UUID userId, UUID orgId) {
        SelectConditionStep<Record1<UUID>> teamIds = select(TEAMS.TEAM_ID)
                .from(TEAMS)
                .where(TEAMS.ORG_ID.eq(orgId));

        return tx.fetchExists(selectFrom(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(userId)
                        .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));
    }

    public Set<UUID> getOrgIds(UUID userId) {
        SelectConditionStep<Record1<UUID>> teamIds = select(V_USER_TEAMS.TEAM_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(userId));

        return dsl().selectDistinct(TEAMS.ORG_ID)
                .from(TEAMS)
                .where(TEAMS.TEAM_ID.in(teamIds))
                .fetchSet(TEAMS.ORG_ID);
    }

    public String getEmail(UUID id) {
        return dsl().select(USERS.USER_EMAIL)
                .from(USERS)
                .where(USERS.USER_ID.eq(id))
                .fetchOne(USERS.USER_EMAIL);
    }

    public void updateRoles(UUID id, Set<String> roles) {
        tx(tx -> updateRoles(tx, id, roles));
    }

    public void updateRoles(DSLContext tx, UUID id, Set<String> roles) {
        tx.deleteFrom(USER_ROLES).where(USER_ROLES.USER_ID.eq(id)).execute();

        tx.insertInto(USER_ROLES).select(
                select(
                        value(id).as(USER_ROLES.USER_ID),
                        ROLES.ROLE_ID.as(USER_ROLES.ROLE_ID)
                ).from(ROLES).where(ROLES.ROLE_NAME.in(roles)))
                .execute();
    }

    public Optional<Boolean> isDisabled(UUID userId) {
        return dsl().select(USERS.IS_DISABLED)
                .from(USERS)
                .where(USERS.USER_ID.eq(userId))
                .fetchOne(r -> Optional.ofNullable(r.get(USERS.IS_DISABLED)));
    }

    // TODO add "include" option
    public List<UserEntry> list(String filter, int offset, int limit) {
        return dsl().select(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, USERS.USER_TYPE, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.IS_DISABLED, USERS.DISABLED_DATE, USERS.IS_PERMANENTLY_DISABLED)
                .from(USERS)
                .where(USERS.IS_DISABLED.isFalse())
                .and(value(filter).isNotNull()
                        .and(USERS.USERNAME.containsIgnoreCase(filter)
                                .or(USERS.DISPLAY_NAME.containsIgnoreCase(filter))
                                .or(USERS.USER_EMAIL.containsIgnoreCase(filter))))
                .orderBy(USERS.DISPLAY_NAME, USERS.USERNAME)
                .offset(offset)
                .limit(limit)
                .fetch(r -> new UserEntry(r.get(USERS.USER_ID),
                        r.get(USERS.USERNAME),
                        r.get(USERS.DOMAIN),
                        r.get(USERS.DISPLAY_NAME),
                        null,
                        UserType.valueOf(r.get(USERS.USER_TYPE)),
                        r.get(USERS.USER_EMAIL),
                        null,
                        r.get(USERS.IS_DISABLED),
                        r.get(USERS.DISABLED_DATE),
                        r.get(USERS.IS_PERMANENTLY_DISABLED)));
    }

    public List<LdapGroupSearchResult> searchLdapGroups(String filter) {
        return dsl().selectDistinct(USER_LDAP_GROUPS.LDAP_GROUP)
                .from(USER_LDAP_GROUPS)
                .where(USER_LDAP_GROUPS.LDAP_GROUP.likeIgnoreCase(String.format("%%%s%%", filter)))
                .orderBy(USER_LDAP_GROUPS.LDAP_GROUP)
                .limit(10)
                .fetch(r -> LdapGroupSearchResult.builder()
                        .displayName(r.get(USER_LDAP_GROUPS.LDAP_GROUP))
                        .groupName(r.get(USER_LDAP_GROUPS.LDAP_GROUP))
                        .build());
    }

    public List<UserTeam> listTeams(DSLContext tx, UUID userId) {
        return tx.select(USER_TEAMS.TEAM_ID, USER_TEAMS.TEAM_ROLE)
                .from(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(userId))
                .fetch(r -> UserTeam.of(r.get(USER_TEAMS.TEAM_ID), TeamRole.valueOf(r.get(USER_TEAMS.TEAM_ROLE))));
    }

    public void excludeFromTeams(DSLContext tx, UUID userId, List<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return;
        }

        tx.deleteFrom(USER_TEAMS)
                .where(USER_TEAMS.USER_ID.eq(userId)
                        .and(USER_TEAMS.TEAM_ID.in(teamIds)))
                .execute();
    }

    private UserEntry getUserInfo(DSLContext tx, Record9<UUID, String, String, String, String, String, Boolean, Boolean, OffsetDateTime> r) {
        // TODO join?

        UserPrincipal p = UserPrincipal.assertCurrent();
        boolean isAdmin = Roles.isAdmin();
        UUID userId = p.getId();

        Field<String> orgNameField = select(ORGANIZATIONS.ORG_NAME)
                .from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_ID.eq(TEAMS.ORG_ID)).asField();

        SelectConditionStep<Record1<UUID>> teamIds = select(V_USER_TEAMS.TEAM_ID)
                .from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(r.get(USERS.USER_ID)));

        List<OrganizationEntry> orgs = tx.selectDistinct(TEAMS.ORG_ID, orgNameField)
                .from(TEAMS)
                .where(TEAMS.TEAM_ID.in(teamIds))
                .fetch(e -> new OrganizationEntry(e.value1(), e.value2(), null, null, null, null));

        SelectConditionStep<Record1<String[]>> permissions = select(arrayAgg(PERMISSIONS.PERMISSION_NAME)).from(PERMISSIONS)
                .where(PERMISSIONS.PERMISSION_ID.in(
                        select(ROLE_PERMISSIONS.PERMISSION_ID).from(ROLE_PERMISSIONS)
                                .where(ROLE_PERMISSIONS.ROLE_ID.in(ROLES.ROLE_ID))));

        SelectConditionStep<Record1<UUID>> roleIds = select(V_USER_ROLES.ROLE_ID).from(V_USER_ROLES)
                .where(V_USER_ROLES.USER_ID
                        .eq(r.get(USERS.USER_ID)));

        List<RoleEntry> roles = tx.select(ROLES.ROLE_ID, ROLES.ROLE_NAME,
                isnull(permissions.asField(), new String[]{}))
                .from(ROLES)
                .where(ROLES.ROLE_ID.in(roleIds))
                .fetch(e -> new RoleEntry(e.value1(), e.value2(), new HashSet<>(Arrays.asList(e.value3()))));

        return new UserEntry(r.get(USERS.USER_ID),
                r.get(USERS.USERNAME),
                r.get(USERS.DOMAIN),
                r.get(USERS.DISPLAY_NAME),
                isAdmin ? new HashSet<>(orgs) : new HashSet<>(),
                UserType.valueOf(r.get(USERS.USER_TYPE)),
                r.get(USERS.USER_EMAIL),
                new HashSet<>(roles),
                r.get(USERS.IS_DISABLED),
                r.get(USERS.DISABLED_DATE),
                r.get(USERS.IS_PERMANENTLY_DISABLED));
    }

    private static String toLowerCase(String s) {
        if (s == null) {
            return null;
        }

        return s.toLowerCase();
    }
}
