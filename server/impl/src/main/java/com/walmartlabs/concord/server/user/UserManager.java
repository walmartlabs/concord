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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.user.UserInfoProvider.UserInfo;

@Named
public class UserManager {

    private final UserDao userDao;
    private final TeamDao teamDao;
    private final AuditLog auditLog;
    private final Map<UserType, UserInfoProvider> userInfoProviders;

    @Inject
    public UserManager(UserDao userDao, TeamDao teamDao, AuditLog auditLog, List<UserInfoProvider> providers) {
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
            result = get(info.username(), info.userDomain(), type);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.of(create(username, userDomain, null, null, type, null));
    }

    public Optional<UserEntry> get(UUID id) {
        return Optional.ofNullable(userDao.get(id));
    }

    public Optional<UUID> getId(String username, String userDomain, UserType type) {
        return Optional.ofNullable(userDao.getId(username, userDomain, type));
    }

    public Optional<UserEntry> update(UUID userId, String displayName, String email, UserType userType, boolean isDisabled, Set<String> roles) {
        UserEntry prevEntry = userDao.get(userId);
        if (prevEntry == null) {
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
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        return userDao.isInOrganization(userId, orgId);
    }

    public UserInfo getCurrentUserInfo() {
        UserPrincipal u = UserPrincipal.getCurrent();
        if (u == null) {
            return null;
        }

        UserInfoProvider p = assertProvider(u.getType());
        return p.getInfo(u.getId(), u.getUsername(), u.getDomain());
    }

    public List<LdapGroupSearchResult> searchLdapGroups(String filter) {
        return userDao.searchLdapGroups(filter);
    }

    public UserInfo getInfo(String username, String domain, UserType type) {
        UserInfoProvider p = assertProvider(type);
        return p.getInfo(null, username, domain);
    }

    public void enable(UUID userId) {
        if (!userDao.isDisabled(userId)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + userId))) {

            // the account is already enabled, nothing to do
            return;
        }

        userDao.enable(userId);

        auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                .field("userId", userId)
                .changes(describeStatusChange(true), describeStatusChange(false))
                .log();
    }

    public void disable(UUID userId) {
        if (userDao.isDisabled(userId)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + userId))) {

            // the account is already disabled, nothing to do
            return;
        }

        userDao.disable(userId);

        auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                .field("userId", userId)
                .changes(describeStatusChange(false), describeStatusChange(true))
                .log();
    }

    private UserInfoProvider assertProvider(UserType type) {
        UserInfoProvider p = userInfoProviders.get(type);
        if (p == null) {
            throw new ConcordApplicationException("Unknown user account type: " + type);
        }
        return p;
    }

    /**
     * {@link com.walmartlabs.concord.server.org.project.DiffUtils#compare(Object, Object)}
     * doesn't work for top-level Maps. So we have to create a temporary bean with a single
     * field in order to record the user account's status change using the existing
     * {@link AuditLog.EntryBuilder#changes(Object, Object)} mechanism w/o pulling
     * the {@link UserEntry} before and after the change.
     */
    private static StatusChange describeStatusChange(boolean disabled) {
        return new StatusChange(disabled);
    }

    private static class StatusChange {

        private final boolean disabled;

        private StatusChange(boolean disabled) {
            this.disabled = disabled;
        }

        public boolean isDisabled() {
            return disabled;
        }
    }
}
