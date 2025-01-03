package com.walmartlabs.concord.server.security.apikey;

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
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import java.util.Arrays;

public class ApiKeyRealm extends AuthorizingRealm {

    private static final String REALM_NAME = "apikey";

    private final UserManager userManager;
    private final AuditLog auditLog;

    @Inject
    public ApiKeyRealm(UserManager userManager, AuditLog auditLog) {
        this.userManager = userManager;
        this.auditLog = auditLog;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof ApiKey;
    }

    @Override
    @WithTimer
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        ApiKey t = (ApiKey) token;

        UserEntry u = null;
        if (t.getUserId() != null) {
            u = userManager.get(t.getUserId()).orElse(null);
            if (u == null) {
                return null;
            }

            if (u.isDisabled()) {
                throw new AuthenticationException("User account '" + u.getName() + "' is disabled");
            }
        }

        auditLog.add(AuditObject.SYSTEM, AuditAction.ACCESS)
                .userId(u != null ? u.getId() : null)
                .field("realm", REALM_NAME)
                .field("apiKeyId", t.getKeyId())
                .log();

        UserPrincipal p = new UserPrincipal(REALM_NAME, u);
        return new SimpleAccount(Arrays.asList(p, t), t.getKey(), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (!REALM_NAME.equals(p.getRealm())) {
            return null;
        }

        return SecurityUtils.toAuthorizationInfo(principals);
    }
}
