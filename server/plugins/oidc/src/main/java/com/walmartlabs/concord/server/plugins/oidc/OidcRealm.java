package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.common.Matcher;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.plugins.oidc.PluginConfiguration.TeamMapping;
import com.walmartlabs.concord.server.role.RoleDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.*;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class OidcRealm extends AuthorizingRealm {

    private static final Logger log = LoggerFactory.getLogger(OidcRealm.class);
    private static final String REALM_NAME = "oidc";

    private final UserManager userManager;
    private final UserDao userDao;
    private final TeamDao teamDao;

    private final Map<String, List<PluginConfiguration.Source>> roleMapping;
    private final Map<UUID, TeamMapping> teamMapping;

    @Inject
    public OidcRealm(PluginConfiguration cfg,
                     UserManager userManager,
                     UserDao userDao,
                     RoleDao roleDao,
                     TeamDao teamDao) {

        this.userManager = userManager;
        this.userDao = userDao;
        this.teamDao = teamDao;

        this.roleMapping = validateRoleMapping(cfg.getRoleMapping(), roleDao);
        this.teamMapping = validateTeamMapping(cfg.getTeamMapping(), teamDao);

        setCredentialsMatcher(new OidcCredentialsMatcher());
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof OidcToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        OidcToken t = (OidcToken) token;

        UserProfile profile = t.getProfile();

        // TODO replace getOrCreate+update with a single method?

        String username = profile.email().toLowerCase();
        UserEntry u = userManager.getOrCreate(username, null, UserType.LOCAL)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + profile.email()));
        UUID userId = u.getId();

        userManager.update(userId, profile.displayName(), profile.email(), null, false, null);

        Set<UUID> newTeams = new HashSet<>();
        teamDao.tx(tx -> {
            List<UserTeam> currentTeams = userDao.listTeams(tx, userId);

            for (Map.Entry<UUID, TeamMapping> e : teamMapping.entrySet()) {
                UUID teamId = e.getKey();
                TeamMapping mapping = e.getValue();
                if (match(profile, mapping.sources())) {
                    if (!hasTeam(teamId, mapping.role(), currentTeams)) {
                        teamDao.upsertUser(tx, teamId, userId, mapping.role());
                    }
                    newTeams.add(teamId);
                }
            }

            List<UUID> toRemove = currentTeams.stream()
                    .map(UserTeam::teamId)
                    .filter(o -> !newTeams.contains(o))
                    .collect(Collectors.toList());
            userDao.excludeFromTeams(tx, userId, toRemove);
        });

        UserPrincipal userPrincipal = new UserPrincipal(REALM_NAME, u);
        return new SimpleAccount(Arrays.asList(userPrincipal, t), t, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (p == null || !REALM_NAME.equals(p.getRealm())) {
            return null;
        }

        OidcToken token = principals.oneByType(OidcToken.class);

        List<String> roles = new ArrayList<>();
        for (Map.Entry<String, List<PluginConfiguration.Source>> e : roleMapping.entrySet()) {
            String roleName = e.getKey();
            List<PluginConfiguration.Source> sources = e.getValue();

            if (match(token.getProfile(), sources)) {
                roles.add(roleName);
            }
        }
        return SecurityUtils.toAuthorizationInfo(principals, roles);
    }

    private static boolean match(UserProfile profile, List<PluginConfiguration.Source> sources) {
        for (PluginConfiguration.Source source : sources) {
            String attr = source.attribute();
            String pattern = source.pattern();
            Object attrValue = profile.getAttribute(attr);
            if (Matcher.matches(attrValue, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTeam(UUID teamId, TeamRole role, List<UserTeam> teams) {
        return teams.stream().anyMatch(ut -> ut.teamId() == teamId && ut.role() == role);
    }

    @VisibleForTesting
    static Map<String, List<PluginConfiguration.Source>> validateRoleMapping(Map<String, List<PluginConfiguration.Source>> input, RoleDao roleDao) {
        for (Map.Entry<String, List<PluginConfiguration.Source>> entry : input.entrySet()) {
            String roleName = entry.getKey();
            if (roleDao.getId(roleName) == null) {
                log.warn("validateRoleMapping -> possibly invalid OIDC role mapping for roleName={}, role not found. It will still be used during user authorization.", roleName);
            }
        }
        return input;
    }

    @VisibleForTesting
    static Map<UUID, TeamMapping> validateTeamMapping(Map<UUID, TeamMapping> input, TeamDao teamDao) {
        Map<UUID, TeamMapping> output = new HashMap<>();

        for (Map.Entry<UUID, TeamMapping> entry : input.entrySet()) {
            boolean valid = true;

            UUID teamId = entry.getKey();
            TeamMapping teamMapping = entry.getValue();

            if (teamDao.get(teamId) == null) {
                log.warn("validateTeamMapping -> invalid OIDC team mapping, teamId={} doesn't exist", teamId);
                valid = false;
            }

            for (PluginConfiguration.Source src : teamMapping.sources()) {
                if (src.attribute() == null || src.attribute().isBlank()) {
                    log.warn("validateTeamMapping -> invalid OIDC team mapping for teamId={}, empty source attribute name in {} mapping", teamId, src);
                    valid = false;
                }
            }

            if (valid) {
                output.put(teamId, teamMapping);
            } else {
                log.warn("validateTeamMapping -> removing invalid teamId={} to mapping={}. It will not be considered during user authorization.", teamId, teamMapping);
            }
        }

        return output;
    }

    static class OidcCredentialsMatcher implements CredentialsMatcher {

        @Override
        public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
            SimpleAccount account = (SimpleAccount) info;

            OidcToken stored = (OidcToken) account.getCredentials();
            OidcToken received = (OidcToken) token;

            String a = stored.getProfile().accessToken();
            String b = received.getProfile().accessToken();

            return a != null && a.equals(b);
        }
    }
}
