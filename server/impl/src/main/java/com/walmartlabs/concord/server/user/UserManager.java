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

import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.project.DiffUtils;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapGroupSearchResult;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.*;

import static com.walmartlabs.concord.server.user.UserInfoProvider.UserInfo;

public class UserManager {

    private final UserDao userDao;
    private final TeamDao teamDao;
    private final AuditLog auditLog;
    private final Map<UserType, UserInfoProvider> userInfoProviders;

    private static final String SSO_REALM_NAME = "sso";

    @Inject
    public UserManager(UserDao userDao, TeamDao teamDao, AuditLog auditLog, Set<UserInfoProvider> providers) {
        this.userDao = userDao;
        this.teamDao = teamDao;
        this.auditLog = auditLog;

        this.userInfoProviders = new HashMap<>();
        providers.forEach(p -> this.userInfoProviders.put(p.getUserType(), p));
    }

    public Optional<UserEntry> get(String username, String domain, UserType type) {
        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UUID id = userDao.getId(username, domain, type);
        if (id == null) {
            return Optional.empty();
        }

        return Optional.of(userDao.get(id));
    }

    public Optional<UserEntry> getOrCreate(String username, String userDomain, UserType type) {
        return getOrCreate(username, userDomain, type, null, null, null);
    }

    public Optional<UserEntry> getOrCreate(String username, String userDomain, UserType type, String displayName, String email, Set<String> roles) {
        Optional<UserEntry> result = get(username, userDomain, type);
        if (result.isPresent()) {
            return result;
        }

        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UserInfoProvider provider = assertProvider(type);
        UserInfo info = provider.getInfo(null, username, userDomain);
        if (info != null) {
            username = info.username();
            userDomain = info.userDomain();
            displayName = info.displayName();
            email = info.email();
            result = get(username, userDomain, type);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.of(create(username, userDomain, displayName, email, type, roles));
    }

    public Optional<UserEntry> get(UUID id) {
        return Optional.ofNullable(userDao.get(id));
    }

    public Optional<UUID> getId(String username, String userDomain, UserType type) {
        return Optional.ofNullable(userDao.getId(username, userDomain, type));
    }

    public Optional<UserEntry> getGitHubAppUser(String installationNodeId, String userNodeId) {
        if (installationNodeId == null || userNodeId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(userDao.getGitHubAppUser(installationNodeId, userNodeId));
    }

    public void createGitHubAppUser(UUID id, String installationNodeId, String userNodeId) {
        if (installationNodeId == null ||  userNodeId == null) {
            return;
        }

        userDao.createGitHubAppUser(id, installationNodeId, userNodeId);
    }

    public Optional<UserEntry> update(UUID userId, String displayName, String email, UserType userType, boolean isDisabled, Set<String> roles) {
        UserEntry prevEntry = userDao.get(userId);
        if (prevEntry == null) {
            return Optional.empty();
        }

        if (prevEntry.isPermanentlyDisabled()) {
            throw new ConcordApplicationException("User is permanently disabled");
        }

        if (prevEntry.isPermanentlyDisabled() && isDisabled) {
            return Optional.empty();
        }

        UserEntry newEntry = userDao.update(userId, displayName, email, userType, isDisabled, roles);
        if (newEntry == null) {
            return Optional.empty();
        }

        Map<String, Object> changes = DiffUtils.compare(prevEntry, newEntry);
        // some callers (e.g. the LDAP realm) update user records regardless of whether there was
        // any actual changes or not
        // add an audit log record only if there was any changes
        if (!changes.isEmpty()) {
            auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                    .field("userId", userId)
                    .field("username", prevEntry.getName())
                    .field("changes", changes)
                    .log();
        }

        return Optional.of(newEntry);
    }

    public UserEntry create(String username, String domain, String displayName, String email, UserType type, Set<String> roles) {
        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UserInfoProvider provider = assertProvider(type);
        UUID id = provider.create(username, domain, displayName, email, roles);

        // add the new user to the default org/team
        UUID teamId = TeamManager.DEFAULT_ORG_TEAM_ID;
        teamDao.upsertUser(teamId, id, TeamRole.MEMBER);

        UserEntry e = userDao.get(id);
        auditLog.add(AuditObject.USER, AuditAction.CREATE)
                .field("userId", id)
                .field("username", e.getName())
                .changes(null, e)
                .log();

        return e;
    }

    public boolean isInOrganization(UUID orgId) {
        return userDao.txResult(tx -> isInOrganization(tx, orgId));
    }

    public boolean isInOrganization(DSLContext tx, UUID orgId) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        return userDao.isInOrganization(tx, userId, orgId);
    }

    public UserInfo getCurrentUserInfo() {
        UserPrincipal u = UserPrincipal.getCurrent();
        if (u == null) {
            return null;
        }
        UserType type = assertSsoUserType(u, u.getType());
        UserInfoProvider p = assertProvider(type);
        return p.getInfo(u.getId(), u.getUsername(), u.getDomain());
    }

    public List<LdapGroupSearchResult> searchLdapGroups(String filter) {
        return userDao.searchLdapGroups(filter);
    }

    public UserInfo getInfo(String username, String domain, UserType type) {
        UserInfoProvider p = assertProvider(assertUserType(username, domain, type));
        return p.getInfo(null, username, domain);
    }

    public void enable(UUID userId) {
        UserEntry user = userDao.get(userId);

        if (user == null) {
            throw new ConcordApplicationException("User not found: " + userId);
        }

        if (!user.isDisabled()) {
            // the account is already enabled, nothing to do
            return;
        }

        if (user.isPermanentlyDisabled()) {
            throw new ConcordApplicationException("User is permanently disabled");
        }

        userDao.enable(userId);

        auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                .field("userId", userId)
                .changes(describeStatusChange(true, false), describeStatusChange(false, false))
                .log();
    }

    public void disable(UUID userId) {
        UserEntry user = userDao.get(userId);
        if (user == null) {
            throw new ConcordApplicationException("User not found: " + userId, Response.Status.NOT_FOUND);
        }

        if (user.isDisabled() && user.getDisabledDate() != null) {
            // the account is already disabled, nothing to do
            return;
        }

        userDao.disable(userId, false);

        auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                .field("userId", userId)
                .changes(describeStatusChange(user.isDisabled(), user.isPermanentlyDisabled()), describeStatusChange(true, false))
                .log();
    }

    public void permanentlyDisable(UUID userId) {
        UserEntry user = userDao.get(userId);
        if (user == null) {
            throw new ConcordApplicationException("User not found: " + userId, Response.Status.NOT_FOUND);
        }

        if (user.isPermanentlyDisabled() && user.isDisabled() && user.getDisabledDate() != null) {
            // nothing to do
            return;
        }

        userDao.disable(userId, true);

        auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                .field("userId", userId)
                .changes(describeStatusChange(user.isDisabled(), user.isPermanentlyDisabled()), describeStatusChange(true, true))
                .log();
    }

    public void delete(UUID userId) {
        UserEntry user = userDao.get(userId);
        if (user == null) {
            return;
        }

        userDao.delete(userId);

        auditLog.add(AuditObject.USER, AuditAction.DELETE)
                .field("userId", userId)
                .field("name", user.getName())
                .log();
    }

    private UserInfoProvider assertProvider(UserType type) {
        UserInfoProvider p = userInfoProviders.get(type);
        if (p == null) {
            throw new ConcordApplicationException("Unknown user account type: " + type);
        }
        return p;
    }

    private UserType assertUserType(String username, String domain, UserType type) {
        /* Override LDAP to SSO type if current user loggedIn via SSO */
        /*
        1. LoggedIn via internal user realm -> domain = null
        2. LoggedIn via ldap realm -> realm != SSO_REALM_NAME
        3. LoggedIn via SSO -> @return UserType.SSO
         */
        if (username != null && domain != null) {
            UserPrincipal u = UserPrincipal.getCurrent();
            if (u != null && u.getUsername().equalsIgnoreCase(username) && u.getDomain().equalsIgnoreCase(domain)) {
                return assertSsoUserType(u, type);
            }
        }
        return type;
    }

    private UserType assertSsoUserType(UserPrincipal u, UserType type) {
        if (u.getRealm().equals(SSO_REALM_NAME)) {
            if (userInfoProviders.get(UserType.SSO) != null) {
                return UserType.SSO;
            }
        }
        return type;
    }

    /**
     * {@link DiffUtils#compare(Object, Object)}
     * doesn't work for top-level Maps. So we have to create a temporary bean with a single
     * field in order to record the user account's status change using the existing
     * {@link AuditLog.EntryBuilder#changes(Object, Object)} mechanism w/o pulling
     * the {@link UserEntry} before and after the change.
     */
    private static StatusChange describeStatusChange(boolean disabled, boolean permanentlyDisabled) {
        return new StatusChange(disabled, permanentlyDisabled);
    }

    private record StatusChange(boolean disabled, boolean permanentlyDisabled) {
    }
}
