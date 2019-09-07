package com.walmartlabs.concord.server.security.sso;

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
import com.walmartlabs.concord.server.security.PrincipalUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
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

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;

@Named
public class SsoRealm extends AuthorizingRealm {

    public static final String REALM_NAME = "sso";

    private final LdapManager ldapManager;
    private final UserManager userManager;
    private final AuditLog auditLog;

    @Inject
    public SsoRealm(LdapManager ldapManager, UserManager userManager, AuditLog auditLog) {
        this.ldapManager = ldapManager;
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

        LdapPrincipal ldapPrincipal;
        try {
            ldapPrincipal = ldapManager.getPrincipal(t.getUsername(), t.getDomain());
        } catch (Exception e) {
            throw new AuthenticationException("LDAP error", e);
        }

        if (ldapPrincipal == null) {
            throw new AuthenticationException("LDAP data not found: " + t.getUsername() + "@" + t.getDomain());
        }

        UserEntry u = userManager.getOrCreate(ldapPrincipal.getUsername(), ldapPrincipal.getDomain(), UserType.LDAP);

        auditLog.add(AuditObject.SYSTEM, AuditAction.ACCESS)
                .userId(u.getId())
                .field("username", u.getName())
                .field("userDomain", u.getDomain())
                .field("realm", REALM_NAME)
                .log();

        UserPrincipal userPrincipal = new UserPrincipal(REALM_NAME, u);
        return new SimpleAccount(Arrays.asList(userPrincipal, t, ldapPrincipal), t.getCredentials(), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (!REALM_NAME.equals(p.getRealm())) {
            return null;
        }

        return PrincipalUtils.toAuthorizationInfo(principals);
    }
}
