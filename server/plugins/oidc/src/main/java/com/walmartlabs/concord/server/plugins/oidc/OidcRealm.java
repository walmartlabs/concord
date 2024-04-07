package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.Matcher;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.security.PrincipalUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.*;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.pac4j.oidc.profile.OidcProfile;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class OidcRealm extends AuthorizingRealm {

    private static final String REALM_NAME = "oidc";

    private final PluginConfiguration cfg;
    private final UserManager userManager;

    private final UserDao userDao;
    private final TeamDao teamDao;

    @Inject
    public OidcRealm(PluginConfiguration cfg, UserManager userManager, UserDao userDao, TeamDao teamDao) {
        this.cfg = cfg;
        this.userManager = userManager;
        this.userDao = userDao;
        this.teamDao = teamDao;

        setCredentialsMatcher((token, info) -> {
            SimpleAccount account = (SimpleAccount) info;

            OidcToken stored = (OidcToken) account.getCredentials();
            OidcToken received = (OidcToken) token;

            Object a = stored.getProfile().getAccessToken();
            Object b = received.getProfile().getAccessToken();

            return a.equals(b);
        });
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof OidcToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        OidcToken t = (OidcToken) token;

        OidcProfile profile = t.getProfile();

        // TODO replace getOrCreate+update with a single method?

        String username = profile.getEmail().toLowerCase();
        UserEntry u = userManager.getOrCreate(username, null, UserType.LOCAL)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + profile.getEmail()));

        userManager.update(u.getId(), profile.getDisplayName(), profile.getEmail(), null, false, null);

        Set<UUID> newTeams = new HashSet<>();
        teamDao.tx(tx -> {
            List<UserTeam> currentTeams = userDao.listTeams(tx, u.getId());

            for (Map.Entry<UUID, PluginConfiguration.TeamMapping> e : cfg.getTeamMapping().entrySet()) {
                if (match(profile, e.getValue().getSources())) {
                    if (!hasTeam(e.getKey(), e.getValue().getRole(), currentTeams)) {
                        teamDao.upsertUser(tx, e.getKey(), u.getId(), e.getValue().getRole());
                    }
                    newTeams.add(e.getKey());
                }
            }

            List<UUID> toRemove = currentTeams.stream()
                    .map(UserTeam::teamId)
                    .filter(o -> !newTeams.contains(o))
                    .collect(Collectors.toList());
            userDao.excludeFromTeams(tx, u.getId(), toRemove);
        });

        UserPrincipal userPrincipal = new UserPrincipal(REALM_NAME, u);
        return new SimpleAccount(Arrays.asList(userPrincipal, t), t, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (!REALM_NAME.equals(p.getRealm())) {
            return null;
        }

        OidcToken token = principals.oneByType(OidcToken.class);

        List<String> roles = new ArrayList<>();
        for (Map.Entry<String, List<PluginConfiguration.Source>> e : cfg.getRoleMapping().entrySet()) {
            if (match(token.getProfile(), e.getValue())) {
                roles.add(e.getKey());
            }
        }
        return PrincipalUtils.toAuthorizationInfo(principals, roles);
    }

    private static boolean match(OidcProfile profile, List<PluginConfiguration.Source> sources) {
        for (PluginConfiguration.Source source : sources) {
            String attr = source.getAttribute();
            String pattern = source.getPattern();
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
}
