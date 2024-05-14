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

import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.SecurityUtils;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.ldap.AbstractLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import java.util.Arrays;
import java.util.UUID;

public class LdapRealm extends AbstractLdapRealm {

    private static final String REALM_NAME = "ldap";

    private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);

    private final UserManager userManager;
    private final LdapManager ldapManager;
    private final LdapGroupManager ldapGroupManager;
    private final LdapContextFactory ldapContextFactory;
    private final AuditLog auditLog;

    @Inject
    public LdapRealm(LdapConfiguration cfg,
                     UserManager userManager,
                     LdapContextFactory ldapContextFactory,
                     LdapManager ldapManager,
                     LdapGroupManager ldapGroupManager,
                     AuditLog auditLog) throws NamingException {

        this.userManager = userManager;
        this.ldapManager = ldapManager;
        this.ldapGroupManager = ldapGroupManager;
        this.auditLog = auditLog;
        this.searchBase = cfg.getSearchBase();
        this.systemUsername = cfg.getSystemUsername();
        this.systemPassword = cfg.getSystemPassword();

        setUrl(cfg.getSystemPassword() != null 
                ? (String) ldapContextFactory.getSystemLdapContext().getEnvironment().get("java.naming.provider.url") 
                : cfg.getUrl());

        setCachingEnabled(false);

        setAuthenticationTokenClass(UsernamePasswordToken.class);

        setCredentialsMatcher((token, info) -> {
            SimpleAccount a = (SimpleAccount) info;

            UsernamePasswordToken stored = (UsernamePasswordToken) a.getCredentials();
            UsernamePasswordToken received = (UsernamePasswordToken) token;

            return stored.getUsername().equals(received.getUsername()) &&
                    Arrays.equals(stored.getPassword(), received.getPassword());
        });

        this.ldapContextFactory = ldapContextFactory;
        setLdapContextFactory(ldapContextFactory);
    }

    @Override
    @WithTimer
    protected AuthenticationInfo queryForAuthenticationInfo(AuthenticationToken token, LdapContextFactory ldapContextFactory) throws NamingException {
        if (this.url == null) {
            return null;
        }

        UsernamePasswordToken t = (UsernamePasswordToken) token;

        LdapPrincipal ldapPrincipal;
        try {
            ldapPrincipal = getPrincipal(t);
        } catch (Exception e) {
            throw new AuthenticationException("LDAP error while attempting to retrieve the user's principal: " + t.getUsername(), e);
        }

        if (ldapPrincipal == null) {
            throw new AuthenticationException("LDAP data not found: " + t.getUsername());
        }

        // TODO merge getOrCreate+update operations into a single one (only for this use case)

        UserEntry u = userManager.getOrCreate(ldapPrincipal.getUsername(), ldapPrincipal.getDomain(), UserType.LDAP)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + ldapPrincipal.getUsername()));

        if (u.isDisabled()) {
            throw new AuthenticationException("User account '" + u.getName() + "' is disabled");
        }

        UUID userId = u.getId();

        u = userManager.update(userId, ldapPrincipal.getDisplayName(), ldapPrincipal.getEmail(), UserType.LDAP, false, null)
                .orElseThrow(() -> new RuntimeException("User record not found: " + userId));

        ldapGroupManager.cacheLdapGroupsIfNeeded(userId, ldapPrincipal.getGroups());

        UserPrincipal userPrincipal = new UserPrincipal(REALM_NAME, u);

        auditLog.add(AuditObject.SYSTEM, AuditAction.ACCESS)
                .userId(userId)
                .field("username", u.getName())
                .field("domain", u.getDomain())
                .field("realm", REALM_NAME)
                .log();

        return new SimpleAccount(Arrays.asList(userPrincipal, t, ldapPrincipal), t, getName());
    }

    @SuppressWarnings("deprecation")
    private LdapPrincipal getPrincipal(UsernamePasswordToken t) throws Exception {
        String username = t.getUsername();
        char[] password = t.getPassword();
        if (username == null || password == null) {
            return null;
        }

        String[] usernameDomain = getUsernameDomain(t);
        LdapPrincipal ldapPrincipal = ldapManager.getPrincipal(usernameDomain[0], usernameDomain[1]);
        if (ldapPrincipal == null) {
            throw new AuthenticationException("LDAP data not found: " + username);
        }

        String principalName = ldapPrincipal.getUserPrincipalName();
        if (principalName == null) {
            principalName = ldapPrincipal.getNameInNamespace();
        }

        if (principalName == null) {
            throw new NamingException("Can't determine the principal name of '" + username + "'");
        }

        LdapContext ctx = null;
        try {
            ctx = ldapContextFactory.getLdapContext(principalName, new String(password));
        } catch (Exception e) {
            log.warn("queryForAuthenticationInfo -> '{}', failed: {}", principalName, e.getMessage());
            throw e;
        } finally {
            LdapUtils.closeContext(ctx);
        }

        return ldapPrincipal;
    }

    private String[] getUsernameDomain(UsernamePasswordToken t) {
        String username = t.getUsername().trim();
        int pos = username.indexOf("@");
        if (pos < 0) {
            return new String[]{username, null};
        }
        return new String[]{username.substring(0, pos), username.substring(pos + 1)};
    }

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals, LdapContextFactory ldapContextFactory) {
        LdapPrincipal p = principals.oneByType(LdapPrincipal.class);
        if (p == null) {
            return null;
        }

        return SecurityUtils.toAuthorizationInfo(principals);
    }
}
