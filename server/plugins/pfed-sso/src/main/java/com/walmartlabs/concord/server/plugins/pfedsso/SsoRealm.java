package com.walmartlabs.concord.server.plugins.pfedsso;

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
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;

public class SsoRealm extends AuthorizingRealm {

    public static final String REALM_NAME = "sso";

    private final UserManager userManager;
    private final AuditLog auditLog;

    @Inject
    public SsoRealm(UserManager userManager, AuditLog auditLog) {
        this.userManager = userManager;
        this.auditLog = auditLog;
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof SsoToken;
    }

    @Override
    @WithTimer
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        SsoToken t = (SsoToken) token;

        if (t.getUsername() == null) {
            return null;
        }

        UserEntry u = userManager.get(t.getUsername(), t.getDomain(), UserType.LDAP)
                .orElse(null);
        if (u == null) {
            u = userManager.create(t.getUsername(), t.getDomain(), t.getDisplayName(), t.getMail(), UserType.SSO, null);
        }

        if (!u.isPermanentlyDisabled()) {
            return null;
        }

        // we consider the account active if the authentication was successful
        userManager.enable(u.getId());

        auditLog.add(AuditObject.SYSTEM, AuditAction.ACCESS)
                .userId(u.getId())
                .field("username", u.getName())
                .field("userDomain", u.getDomain())
                .field("realm", REALM_NAME)
                .log();

        UserPrincipal userPrincipal = new UserPrincipal(REALM_NAME, u);

        LdapPrincipal ldapPrincipal = new LdapPrincipal(t.getUsername(), t.getDomain(), t.getNameInNamespace(), t.getUserPrincipalName(), t.getDisplayName(), t.getMail(), t.getGroups(), Collections.singletonMap("mail", t.getMail()));

        return new SimpleAccount(Arrays.asList(userPrincipal, t, ldapPrincipal), t.getCredentials(), getName());
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
