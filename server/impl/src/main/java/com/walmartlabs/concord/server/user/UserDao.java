package com.walmartlabs.concord.server.user;

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
import com.walmartlabs.concord.server.jooq.tables.records.UsersRecord;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Roles.ROLES;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserRoles.USER_ROLES;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

@Named
public class UserDao extends AbstractDao {

    @Inject
    public UserDao(@MainDB Configuration cfg) {
        super(cfg);
    }

    public UUID insert(String username, String domain, String displayName, String email, UserType type, Set<String> roles) {
        return txResult(tx -> insert(tx, username, domain, null, null, type, roles));
    }

    public UUID insert(DSLContext tx, String username, String domain, String displayName, String email, UserType type, Set<String> roles) {
        UUID userId = tx.insertInto(USERS)
                .columns(USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.USER_TYPE)
                .values(username, domain, displayName, email, type.toString())
                .onConflict(USERS.USERNAME, USERS.DOMAIN, USERS.USER_TYPE)
                .doUpdate()
                .set(USERS.DISPLAY_NAME, displayName)
                .set(USERS.USER_EMAIL, email)
                .returning(USERS.USER_ID)
                .fetchOne().getUserId();

        if (roles != null) {
            updateRoles(tx, userId, roles);
        }

        return userId;
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(USERS)
                .where(USERS.USER_ID.eq(id))
                .execute());
    }

    public void disable(UUID id) {
        tx(tx -> tx.update(USERS)
                .set(USERS.IS_DISABLED, inline(true))
                .where(USERS.USER_ID.eq(id))
                .execute());
    }

    public UserEntry update(UUID id, String displayName, String email, UserType userType, boolean isDisabled, Set<String> roles) {
        tx(tx -> {
            UpdateSetMoreStep<UsersRecord> q = tx.update(USERS)
                    .set(USERS.IS_DISABLED, isDisabled);

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
        try (DSLContext tx = DSL.using(cfg)) {
            Record7<UUID, String, String, String, String, String, Boolean> r =
                    tx.select(USERS.USER_ID, USERS.USER_TYPE, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.IS_DISABLED)
                            .from(USERS)
                            .where(USERS.USER_ID.eq(id))
                            .fetchOne();

            if (r == null) {
                return null;
            }

            return getUserInfo(tx, r);
        }
    }

    private UserEntry getUserInfo(DSLContext tx, Record7<UUID, String, String, String, String, String, Boolean> r) {
        // TODO join?
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

        SelectConditionStep<Record1<UUID>> roleIds = select(USER_ROLES.ROLE_ID).from(USER_ROLES)
                .where(USER_ROLES.USER_ID
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
                new HashSet<>(orgs),
                UserType.valueOf(r.get(USERS.USER_TYPE)),
                r.get(USERS.USER_EMAIL),
                new HashSet<>(roles),
                r.get(USERS.IS_DISABLED));
    }

    public UUID getId(String username, String userDomain, UserType type) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<Record1<UUID>> q = tx.select(USERS.USER_ID)
                    .from(USERS)
                    .where(USERS.USERNAME.eq(username));

            if (userDomain != null) {
                q.and(USERS.DOMAIN.eq(userDomain));
            }

            if (type != null) {
                q.and(USERS.USER_TYPE.eq(type.toString()));
            }

            List<UUID> result = q.fetch(USERS.USER_ID);
            if (result.isEmpty()) {
                return null;
            } else if (result.size() > 1) {
                throw new IllegalArgumentException("Non unique results found for username: '" + username + "', domain: '" + userDomain + "', type: " + type);
            }

            return result.get(0);
        }
    }

    public String getUsername(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(USERS.USERNAME).from(USERS)
                    .where(USERS.USER_ID.eq(id))
                    .fetchOne(USERS.USERNAME);
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

            return tx.fetchExists(selectFrom(V_USER_TEAMS)
                    .where(V_USER_TEAMS.USER_ID.eq(userId)
                            .and(V_USER_TEAMS.TEAM_ID.in(teamIds))));
        }
    }

    public Set<UUID> getOrgIds(UUID userId) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<Record1<UUID>> teamIds = select(V_USER_TEAMS.TEAM_ID)
                    .from(V_USER_TEAMS)
                    .where(V_USER_TEAMS.USER_ID.eq(userId));

            return tx.selectDistinct(TEAMS.ORG_ID)
                    .from(TEAMS)
                    .where(TEAMS.TEAM_ID.in(teamIds))
                    .fetchSet(TEAMS.ORG_ID);
        }
    }

    public String getEmail(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(USERS.USER_EMAIL)
                    .from(USERS)
                    .where(USERS.USER_ID.eq(id))
                    .fetchOne(USERS.USER_EMAIL);
        }
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

    public void updateDomain(UUID id, String userDomain) {
        tx(tx -> {
            tx.update(USERS)
                    .set(USERS.DOMAIN, value(userDomain))
                    .where(USERS.USER_ID.eq(id))
                    .execute();
        });
    }

    // TODO add "include" option
    public List<UserEntry> list(String filter, int offset, int limit) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, USERS.USER_TYPE, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.IS_DISABLED)
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
                            r.get(USERS.IS_DISABLED)));
        }
    }
}
