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

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.PrincipalUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.pac4j.oidc.profile.OidcProfile;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;

@Named
public class OidcRealm extends AuthorizingRealm {

    private static final String REALM_NAME = "oidc";

    private final PluginConfiguration cfg;
    private final UserManager userManager;

    @Inject
    public OidcRealm(PluginConfiguration cfg, UserManager userManager) {
        this.cfg = cfg;
        this.userManager = userManager;

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

        UserEntry u = userManager.getOrCreate(profile.getEmail(), null, UserType.LOCAL)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + profile.getEmail()));
        userManager.update(u.getId(), profile.getDisplayName(), profile.getEmail(), null, false, null);

        UserPrincipal userPrincipal = new UserPrincipal(REALM_NAME, u);
        return new SimpleAccount(Arrays.asList(userPrincipal, t), t, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (!REALM_NAME.equals(p.getRealm())) {
            return null;
        }

        return PrincipalUtils.toAuthorizationInfo(principals, cfg.getRoles());
    }
}
