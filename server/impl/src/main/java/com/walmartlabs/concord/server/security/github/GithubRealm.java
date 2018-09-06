package com.walmartlabs.concord.server.security.github;

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

import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyRealm;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.UUID;

@Named
public class GithubRealm extends AuthorizingRealm {

    private static final Logger log = LoggerFactory.getLogger(GithubRealm.class);

    private static final String REALM_NAME = "github";
    private static final String USER = "github";

    private final UserManager userManager;

    @Inject
    public GithubRealm(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof GithubKey;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        GithubKey t = (GithubKey) token;

        try {
            UUID userId = userManager.getId(USER).orElse(null);
            if (userId == null) {
                return null;
            }

            return userManager.get(userId)
                    .map(u -> {
                        UserPrincipal p = new UserPrincipal(REALM_NAME, u);
                        return new SimpleAccount(Arrays.asList(p, t), t.getKey(), getName());
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.error("doGetAuthenticationInfo -> error", e);
            throw e;
        }
    }

    @Override
    @WithTimer
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = (UserPrincipal) principals.getPrimaryPrincipal();
        if (!REALM_NAME.equals(p.getRealm())) {
            return null;
        }
        return new SimpleAuthorizationInfo();
    }
}
