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

import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.security.PrincipalUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.UUID;

public class GithubRealm extends AuthorizingRealm {

    private static final Logger log = LoggerFactory.getLogger(GithubRealm.class);

    private static final String REALM_NAME = "github";
    private static final UUID USER_ID = UUID.fromString("acc17a02-b471-46af-9914-48cba3dd31ab"); // as in v0.47.0.xml

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
            return userManager.get(USER_ID)
                    .map(u -> {
                        UserPrincipal p = new UserPrincipal(REALM_NAME, u);
                        return new SimpleAccount(Arrays.asList(p, t), t.getCredentials(), getName());
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
        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (!REALM_NAME.equals(p.getRealm())) {
            return null;
        }

        return PrincipalUtils.toAuthorizationInfo(principals);
    }
}
