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
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.PrincipalNormalization;
import com.walmartlabs.concord.server.security.ldap.LdapGroupSearchResult;
import com.walmartlabs.concord.server.sdk.security.UserProfileService;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.sdk.security.DirectoryQuery;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.security.Roles;
import java.util.stream.Collectors;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.*;


public class UserManager {

    private final UserDao userDao;
    private final TeamDao teamDao;
    private final AuditLog auditLog;
    private final UserProfileService profiles;
    private final AuthorizationService authorization;
    private final ProviderRegistry registry;
    private final IdentityDao identities;

    @Inject
    public UserManager(UserDao userDao, TeamDao teamDao, AuditLog auditLog, UserProfileService profiles,
                       AuthorizationService authorization, ProviderRegistry registry, IdentityDao identities) {
        this.userDao = userDao;
        this.teamDao = teamDao;
        this.auditLog = auditLog;
        this.profiles = profiles;
        this.authorization = authorization;
        this.registry = registry;
        this.identities = identities;
    }

    public Optional<UserEntry> get(String username, String domain, UserType type) {
        if (type == null) {
            type = currentLegacyType();
        }

        UUID id = userDao.getId(username, domain, type);
        if (id == null) {
            return Optional.empty();
        }

        return Optional.of(userDao.get(id));
    }


    public Optional<UserEntry> get(UUID id) {
        return Optional.ofNullable(userDao.get(id));
    }

    public Optional<UUID> getId(String username, String userDomain, UserType type) {
        return Optional.ofNullable(userDao.getId(username, userDomain, type));
    }

    public Optional<UserEntry> getUserFromExternalMapping(String externalId) {
        if (externalId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(userDao.getUserFromExternalMapping(externalId));
    }

    public void createExternalUserMapping(UUID concordUserId, String externalId) {
        if (externalId == null) {
            return;
        }

        userDao.createExternalUserMapping(concordUserId, externalId);
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
            type = currentLegacyType();
        }

        if (type == UserType.LDAP && !Roles.isAdmin()
                && Optional.ofNullable(registry.policies().get("ldap")).map(p -> !p.autoCreate()).orElse(true)) {
            throw new ConcordApplicationException("Automatic creation of users is disabled.");
        }
        UUID id = userDao.insertOrUpdate(username, domain, displayName, email, type, roles);

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

    /** Records the existing account-creation audit after the identity transaction commits. */
    public void auditIdentityCreation(UUID id) {
        UserEntry e = userDao.get(id);
        auditLog.add(AuditObject.USER, AuditAction.CREATE)
                .field("userId", id)
                .field("username", e.getName())
                .changes(null, e)
                .log();
    }

    public CreateUserResponse updateSelected(UUID id, CreateUserRequest request) {
        UserEntry previous = userDao.get(id);
        UserEntry updated = userDao.updateSelected(id, request);
        if (updated == null) {
            throw new ConcordApplicationException("User not found: " + id, javax.ws.rs.core.Response.Status.NOT_FOUND);
        }
        if (previous != null) {
            Map<String, Object> changes = DiffUtils.compare(previous, updated);
            if (!changes.isEmpty()) {
                auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                        .field("userId", id).field("username", updated.getName()).field("changes", changes).log();
            }
        }
        return new CreateUserResponse(id, updated.getName(), com.walmartlabs.concord.server.OperationResult.UPDATED);
    }

    public CreateUserResponse upsertLegacy(CreateUserRequest request, UserType type) {
        String username = request.getUsername();
        String domain = request.getUserDomain();
        UUID previousId = userDao.getLegacyId(username, domain, type);
        UserInfo creationInfo = null;
        if (previousId == null && type == UserType.LDAP) {
            UserProfile profile = ldapProfile(username, domain);
            if (profile == null) {
                throw new ConcordApplicationException("User '" + username + "' with domain '" + domain + "' not found in LDAP");
            }
            creationInfo = UserInfo.builder().username(profile.username()).userDomain(profile.domain())
                    .displayName(profile.displayName()).email(profile.email()).build();
        }
        UserDao.LegacyUpsert result = userDao.upsertLegacyAccount(username, domain, type, request.getDisplayName(),
                request.getEmail(), request.isDisabled(), request.getRoles(), creationInfo);
        UserEntry entry = userDao.get(result.id());
        if (result.created()) {
            auditIdentityCreation(result.id());
        } else if (result.previous() != null) {
            Map<String, Object> changes = DiffUtils.compare(result.previous(), entry);
            if (!changes.isEmpty()) {
                auditLog.add(AuditObject.USER, AuditAction.UPDATE)
                        .field("userId", result.id()).field("username", entry.getName()).field("changes", changes).log();
            }
        }
        return new CreateUserResponse(result.id(), entry.getName(),
                result.created() ? com.walmartlabs.concord.server.OperationResult.CREATED : com.walmartlabs.concord.server.OperationResult.UPDATED);
    }

    public boolean isInOrganization(UUID orgId) {
        return userDao.txResult(tx -> isInOrganization(tx, orgId));
    }

    public boolean isInOrganization(DSLContext tx, UUID orgId) {
        return userDao.isInOrganization(tx, PrincipalNormalization.assertCurrent().userId(), orgId);
    }

    public UserInfo getCurrentUserInfo() {
        SubjectPrincipal u = PrincipalNormalization.getCurrent();
        if (u == null) {
            return null;
        }
        return getInfo(u.userId());
    }

    public List<LdapGroupSearchResult> searchLdapGroups(String filter) {
        return userDao.searchLdapGroups(filter);
    }

    public UserInfo getInfo(UUID userId) {
        UserProfile profile = profiles.get(userId);
        var groups = authorization.getGroups(userId);
        return UserInfo.builder().id(userId).username(profile.username()).userDomain(profile.domain())
                .displayName(profile.displayName()).email(profile.email()).attributes(profile.attributes())
                .groups(groups.stream().filter(g -> "ldap".equals(g.sourceId()))
                        .map(g -> g.groupId()).collect(Collectors.toSet()))
                .authorizationGroups(groups).build();
    }

    public IdentityDao.ProfileSelection profileSelection(UUID userId) {
        return identities.profile(userId).orElseThrow(() -> new ConcordApplicationException("User not found: " + userId));
    }

    private UserProfile ldapProfile(String username, String domain) {
        var directory = registry.directories().get("ldap");
        if (directory == null) throw new ConcordApplicationException("LDAP directory is unavailable");
        return directory.find(new DirectoryQuery("username", username, domain)).map(e -> e.profile()).orElse(null);
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

    /** Temporary default for legacy username/type selectors. */
    public UserType currentLegacyType() {
        UUID id = PrincipalNormalization.assertCurrent().userId();
        return get(id).orElseThrow(() -> new ConcordApplicationException("User not found: " + id)).getType();
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
