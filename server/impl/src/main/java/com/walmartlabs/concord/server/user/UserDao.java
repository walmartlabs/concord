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

import com.typesafe.config.Config;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.jooq.Tables;
import com.walmartlabs.concord.server.jooq.tables.records.UsersRecord;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.security.ldap.LdapUserDirectory;
import com.walmartlabs.concord.server.security.ldap.LdapGroupSearchResult;
import org.jooq.*;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Roles.ROLES;
import static com.walmartlabs.concord.server.jooq.tables.Teams.TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.UserRoles.USER_ROLES;
import static com.walmartlabs.concord.server.jooq.tables.UserTeams.USER_TEAMS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static java.util.Objects.requireNonNull;
import static org.jooq.impl.DSL.*;

public class UserDao extends AbstractDao {

    private final UuidGenerator uuidGenerator;
    private final Config config;

    @Inject
    public UserDao(@MainDB Configuration cfg, UuidGenerator uuidGenerator, Config config) {
        super(cfg);
        this.uuidGenerator = requireNonNull(uuidGenerator);
        this.config = requireNonNull(config);
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
            tx.execute("select pg_advisory_xact_lock(2461020)");
            UUID id = legacyId(tx, username, domain, type);
            if (id == null) {
                id = insertLegacy(tx, username, domain, displayName, email, type);
            } else {
                tx.update(USERS).set(USERS.DISPLAY_NAME, displayName).set(USERS.USER_EMAIL, email)
                        .where(USERS.USER_ID.eq(id)).execute();
            }
            if (roles != null) {
                updateRoles(tx, id, roles);
            }
            return id;
        });
    }

    private UUID legacyId(DSLContext tx, String username, String domain, UserType type) {
        List<UUID> matches = tx.select(USERS.USER_ID).from(USERS)
                .where(USERS.USERNAME.eq(toLowerCase(username)))
                .and(USERS.DOMAIN.isNotDistinctFrom(toLowerCase(domain)))
                .and(USERS.USER_TYPE.eq(type.name())).fetch(USERS.USER_ID);
        if (matches.size() > 1) {
            throw new ConcordApplicationException("Ambiguous legacy account: " + username, Response.Status.CONFLICT);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    public UUID getLegacyId(String username, String domain, UserType type) {
        return legacyId(dsl(), username, domain, type);
    }

    private UUID insertLegacy(DSLContext tx, String username, String domain, String displayName, String email, UserType type) {
        UUID id = uuidGenerator.generate();
        tx.insertInto(USERS)
                .columns(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.USER_TYPE)
                .values(id, toLowerCase(username), toLowerCase(domain), displayName, email, type.name()).execute();
        if (type == UserType.LOCAL || type == UserType.LDAP) {
            ExternalIdentityKey key = type == UserType.LOCAL
                    ? new ExternalIdentityKey("local", "", id.toString()) : LdapUserDirectory.key(username, domain);
            UUID identityId = uuidGenerator.generate();
            tx.execute("insert into user_identities (identity_id, user_id, provider_id, authority, subject, is_active) values (?, ?, ?, ?, ?, true)",
                    identityId, id, key.providerId(), key.authority(), key.subject());
            if (type == UserType.LDAP) {
                tx.execute("update users set profile_provider_id = 'ldap', profile_identity_id = ? where user_id = ?", identityId, id);
                String qualified = "com.walmartlabs.concord.server.security.authorization.sources.ldap.required";
                String plain = "security.authorization.sources.ldap.required";
                boolean required = config.hasPath(qualified) ? config.getBoolean(qualified) :
                        config.hasPath(plain) && config.getBoolean(plain);
                tx.execute("insert into authorization_sources(source_id,is_active) values ('ldap',false) on conflict do nothing");
                tx.execute("insert into user_authorization_bindings(binding_id,user_id,source_id,identity_id,is_required,origin,refresh_version) values (?,?,?,?,?,'DEFAULT',0)",
                        uuidGenerator.generate(), id, "ldap", identityId, required);
            }
        }
        return id;
    }

    public record LegacyUpsert(UUID id, boolean created, UserEntry previous) {}

    public LegacyUpsert upsertLegacyAccount(String username, String domain, UserType type, String displayName,
                                            String email, boolean disabled, Set<String> roles, UserInfoProvider.UserInfo creationInfo) {
        return txResult(tx -> {
            tx.execute("select pg_advisory_xact_lock(2461020)");
            UUID id = legacyId(tx, username, domain, type);
            if (id == null && creationInfo != null) {
                id = legacyId(tx, creationInfo.username(), creationInfo.userDomain(), type);
            }
            boolean created = id == null;
            UserEntry previous = null;
            if (created) {
                if (creationInfo != null) {
                    id = insertLegacy(tx, creationInfo.username(), creationInfo.userDomain(), creationInfo.displayName(), creationInfo.email(), type);
                } else {
                    id = insertLegacy(tx, username, domain, displayName, email, type);
                }
                tx.insertInto(USER_TEAMS).columns(USER_TEAMS.TEAM_ID, USER_TEAMS.USER_ID, USER_TEAMS.TEAM_ROLE)
                        .values(TeamManager.DEFAULT_ORG_TEAM_ID, id, TeamRole.MEMBER.name()).onConflictDoNothing().execute();
            } else {
                var row = tx.select(USERS.USER_ID, USERS.USER_TYPE, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME,
                                USERS.USER_EMAIL, USERS.IS_DISABLED, USERS.IS_PERMANENTLY_DISABLED, USERS.DISABLED_DATE)
                        .from(USERS).where(USERS.USER_ID.eq(id)).forUpdate().fetchOne();
                if (Boolean.TRUE.equals(row.get(USERS.IS_PERMANENTLY_DISABLED))) {
                    throw new ConcordApplicationException("User is permanently disabled");
                }
                previous = getUserInfo(tx, row);
                var update = tx.update(USERS).set(USERS.IS_DISABLED, disabled);
                if (disabled) {
                    update.set(USERS.DISABLED_DATE, currentOffsetDateTime());
                } else {
                    update.setNull(USERS.DISABLED_DATE);
                }
                if (displayName != null) update.set(USERS.DISPLAY_NAME, displayName);
                if (email != null) update.set(USERS.USER_EMAIL, email);
                update.where(USERS.USER_ID.eq(id)).execute();
            }
            if (roles != null) updateRoles(tx, id, roles);
            return new LegacyUpsert(id, created, previous);
        });
    }

    /** Creates an identity-owned account and its default team membership in the caller's transaction. */
    public void createIdentityAccount(DSLContext tx, UUID userId, UserProfile profile, String legacyType) {
        if (legacyType != null) {
            UserType.valueOf(legacyType);
        }
        tx.insertInto(USERS)
                .columns(USERS.USER_ID, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.USER_TYPE)
                .values(userId, profile.username(), profile.domain(), profile.displayName(), profile.email(), legacyType)
                .execute();
        tx.insertInto(USER_TEAMS)
                .columns(USER_TEAMS.TEAM_ID, USER_TEAMS.USER_ID, USER_TEAMS.TEAM_ROLE)
                .values(TeamManager.DEFAULT_ORG_TEAM_ID, userId, TeamRole.MEMBER.name())
                .onConflictDoNothing().execute();
    }

    public void delete(UUID id) {
        tx(tx -> {
            tx.execute("update user_identities set is_active = false where user_id = ?", id);
            tx.deleteFrom(USERS).where(USERS.USER_ID.eq(id)).execute();
        });
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

    /** Updates profile fields without ever changing administrative account status. */
    public UserEntry updateProfile(UUID id, String displayName, String email) {
        if (displayName != null || email != null) {
            tx(tx -> {
                UpdateSetMoreStep<UsersRecord> q = tx.update(USERS).set(USERS.USER_ID, USERS.USER_ID);
                if (displayName != null) {
                    q.set(USERS.DISPLAY_NAME, displayName);
                }
                if (email != null) {
                    q.set(USERS.USER_EMAIL, email);
                }
                q.where(USERS.USER_ID.eq(id)).execute();
            });
        }
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

        return getUserInfo(tx, r);
    }

    public UserEntry getUserFromExternalMapping(String externalId) {
        DSLContext tx = dsl();

        Record9<UUID, String, String, String, String, String, Boolean, Boolean, OffsetDateTime> r =
                tx.select(USERS.USER_ID, USERS.USER_TYPE, USERS.USERNAME, USERS.DOMAIN, USERS.DISPLAY_NAME, USERS.USER_EMAIL, USERS.IS_DISABLED, USERS.IS_PERMANENTLY_DISABLED, USERS.DISABLED_DATE)
                        .from(USERS, EXTERNAL_APP_USERS)
                        .where(USERS.USER_ID.eq(EXTERNAL_APP_USERS.USER_ID))
                        .and(EXTERNAL_APP_USERS.EXTERNAL_USER_ID.eq(externalId))
                        .fetchOne();

        if (r == null) {
            return null;
        }

        return getUserInfo(tx, r);
    }

    /**
     * Creates a mapping from an external user to an existing Concord user. The
     * external identifier may not be a singular, exact, ID for the system. The
     * context(s) creating and retrieving the mapping must understand the construction
     * of the identifier. Typically, it should be a formatting along the lines
     * of <code>{external_system} + {actual_user_id}</code> to avoid clashing of
     * IDs that are unique to the system but potentially duplicated across other
     * external systems that have been integrated (e.g. multiple GitHub instances).
     * @param userId existing Concord user ID
     * @param externalId identifier for user in an external system
     */
    public void createExternalUserMapping(UUID userId, String externalId) {
        tx(tx -> tx.insertInto(EXTERNAL_APP_USERS)
                .columns(EXTERNAL_APP_USERS.EXTERNAL_USER_ID, EXTERNAL_APP_USERS.USER_ID)
                .values(externalId, userId)
                .onConflict(EXTERNAL_APP_USERS.EXTERNAL_USER_ID)
                .doUpdate()
                .set(EXTERNAL_APP_USERS.USER_ID, userId)
                .execute());
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
            throw new ConcordApplicationException("Non unique results found for username: '" + username + "', domain: '" + domain + "', type: " + type +
                    ". Note that user and domain names are case-insensitive.", Response.Status.CONFLICT);
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
                        UserType.fromNullable(r.get(USERS.USER_TYPE)),
                        r.get(USERS.USER_EMAIL),
                        null,
                        r.get(USERS.IS_DISABLED),
                        r.get(USERS.DISABLED_DATE),
                        r.get(USERS.IS_PERMANENTLY_DISABLED)));
    }

    public List<LdapGroupSearchResult> searchLdapGroups(String filter) {
        return dsl().fetch("SELECT DISTINCT group_id FROM v_effective_source_groups " +
                        "WHERE source_id = 'ldap' AND group_id ILIKE ? ORDER BY group_id LIMIT 10",
                "%" + filter + "%")
                .map(r -> LdapGroupSearchResult.builder()
                        .displayName(r.get(0, String.class))
                        .groupName(r.get(0, String.class))
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
                new HashSet<>(orgs),
                UserType.fromNullable(r.get(USERS.USER_TYPE)),
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

        return s.toLowerCase(Locale.ROOT);
    }
}
