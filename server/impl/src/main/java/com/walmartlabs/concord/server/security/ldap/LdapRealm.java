package com.walmartlabs.concord.server.security.ldap;

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


import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.api.user.UserType;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.ldap.AbstractLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import java.util.Arrays;

@Named
public class LdapRealm extends AbstractLdapRealm {

    public static final String REALM_NAME = "ldap";
    
    private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);

    private final UserManager userManager;
    private final LdapManager ldapManager;
    private final AuditLog auditLog;

    @Inject
    public LdapRealm(LdapConfiguration cfg,
                     UserManager userManager,
                     ConcordLdapContextFactory ctxFactory,
                     LdapManager ldapManager,
                     AuditLog auditLog) {

        this.userManager = userManager;
        this.ldapManager = ldapManager;
        this.auditLog = auditLog;

        this.url = cfg.getUrl();
        this.searchBase = cfg.getSearchBase();
        this.systemUsername = cfg.getSystemUsername();
        this.systemPassword = cfg.getSystemPassword();

        setCachingEnabled(false);

        setAuthenticationTokenClass(UsernamePasswordToken.class);

        setCredentialsMatcher((token, info) -> {
            SimpleAccount a = (SimpleAccount) info;

            UsernamePasswordToken stored = (UsernamePasswordToken) a.getCredentials();
            UsernamePasswordToken received = (UsernamePasswordToken) token;

            return stored.getUsername().equals(received.getUsername()) &&
                    Arrays.equals(stored.getPassword(), received.getPassword());
        });

        setLdapContextFactory(ctxFactory);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected AuthenticationInfo queryForAuthenticationInfo(AuthenticationToken token, LdapContextFactory ldapContextFactory) throws NamingException {
        if (this.url == null) {
            return null;
        }

        UsernamePasswordToken t = (UsernamePasswordToken) token;

        String username = t.getUsername();
        char[] password = t.getPassword();
        if (username == null || password == null) {
            return null;
        }

        username = username.trim();

        LdapPrincipal ldapPrincipal = ldapManager.getPrincipal(username);
        if (ldapPrincipal == null) {
            throw new AuthenticationException("LDAP data not found: " + username);
        }

        String principalName = ldapPrincipal.getNameInNamespace();

        LdapContext ctx = null;
        try {
            ctx = ldapContextFactory.getLdapContext(principalName, new String(password));
            log.info("queryForAuthenticationInfo -> '{}', success", principalName);
        } catch (Exception e) {
            log.warn("queryForAuthenticationInfo -> '{}', failed: {}", principalName, e.getMessage());
            throw e;
        } finally {
            LdapUtils.closeContext(ctx);
        }

        UserEntry user = userManager.getOrCreate(ldapPrincipal.getUsername(), UserType.LDAP);
        UserPrincipal userPrincipal = new UserPrincipal(REALM_NAME, user);

        auditLog.add(AuditObject.SYSTEM, AuditAction.ACCESS)
                .userId(user.getId())
                .field("realm", REALM_NAME);

        return new SimpleAccount(Arrays.asList(userPrincipal, t, ldapPrincipal), t, getName());
    }

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals, LdapContextFactory ldapContextFactory) {
        LdapPrincipal p = principals.oneByType(LdapPrincipal.class);
        if (p == null) {
            return null;
        }

        return new SimpleAuthorizationInfo();
    }
}
