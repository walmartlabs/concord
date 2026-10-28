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
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.IdentityService;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.sdk.security.VerifiedIdentity;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class LdapRealm extends AuthenticatingRealm {

    public static final String REALM_NAME = "ldap";
    private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);

    private final LdapConfiguration cfg;
    private final IdentityService identities;
    private final LdapManager ldapManager;
    private final LdapContextFactory ldapContextFactory;
    private final AuditLog auditLog;

    @Inject
    public LdapRealm(LdapConfiguration cfg, IdentityService identities, LdapContextFactory ldapContextFactory,
                     LdapManager ldapManager, AuditLog auditLog) {
        this.cfg = cfg;
        this.identities = identities;
        this.ldapManager = ldapManager;
        this.ldapContextFactory = ldapContextFactory;
        this.auditLog = auditLog;
        setName(REALM_NAME);
        setCachingEnabled(false);
        setAuthenticationTokenClass(UsernamePasswordToken.class);
        setCredentialsMatcher((token, info) -> {
            UsernamePasswordToken stored = (UsernamePasswordToken) info.getCredentials();
            UsernamePasswordToken received = (UsernamePasswordToken) token;
            return stored.getUsername().equals(received.getUsername()) &&
                    Arrays.equals(stored.getPassword(), received.getPassword());
        });
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return cfg.isAuthenticationEnabled() && super.supports(token);
    }

    @Override
    @WithTimer
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        if (!cfg.isAuthenticationEnabled() ||
                ((cfg.getUrl() == null || cfg.getUrl().isBlank()) &&
                        (cfg.getDnsSRVName() == null || cfg.getDnsSRVName().isBlank()))) {
            return null;
        }
        UsernamePasswordToken t = (UsernamePasswordToken) token;
        LdapPrincipal ldapPrincipal;
        try {
            ldapPrincipal = getPrincipal(t);
        } catch (Exception e) {
            throw new AuthenticationException("LDAP error while attempting to authenticate: " + t.getUsername(), e);
        }
        if (ldapPrincipal == null) {
            throw new AuthenticationException("LDAP data not found: " + t.getUsername());
        }

        UserProfile profile = new UserProfile(ldapPrincipal.getUsername(), ldapPrincipal.getDomain(),
                ldapPrincipal.getDisplayName(), ldapPrincipal.getEmail(), Map.of());
        VerifiedIdentity verified = new VerifiedIdentity(LdapUserDirectory.key(profile.username(), profile.domain()),
                profile, Set.of(), Map.of(), null, null);
        SubjectPrincipal principal = identities.resolve(verified, new AuthenticationContext(REALM_NAME, Instant.now(), null));
        auditLog.add(AuditObject.SYSTEM, AuditAction.ACCESS)
                .userId(principal.userId())
                .field("username", principal.username())
                .field("domain", principal.domain())
                .field("realm", REALM_NAME)
                .log();
        // The token is retained as a principal for the existing remember-me serializer.
        return new SimpleAccount(Arrays.asList(principal, t), t, getName());
    }

    @SuppressWarnings("deprecation")
    private LdapPrincipal getPrincipal(UsernamePasswordToken t) throws Exception {
        String username = t.getUsername();
        char[] password = t.getPassword();
        // An empty password can result in an anonymous LDAP bind, not credential verification.
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            return null;
        }
        String[] usernameDomain = getUsernameDomain(t);
        LdapPrincipal principal = ldapManager.getPrincipal(usernameDomain[0], usernameDomain[1]);
        if (principal == null) {
            throw new AuthenticationException("LDAP data not found: " + username);
        }
        String principalName = principal.getUserPrincipalName();
        if (principalName == null) {
            principalName = principal.getNameInNamespace();
        }
        if (principalName == null) {
            throw new NamingException("Can't determine the principal name of '" + username + "'");
        }
        LdapContext ctx = null;
        try {
            ctx = ldapContextFactory.getLdapContext(principalName, new String(password));
        } catch (Exception e) {
            log.warn("LDAP bind failed for '{}': {}", principalName, e.getMessage());
            throw e;
        } finally {
            LdapUtils.closeContext(ctx);
        }
        return principal;
    }

    private static String[] getUsernameDomain(UsernamePasswordToken t) {
        String username = t.getUsername().trim();
        int pos = username.indexOf('@');
        if (pos < 0) {
            return new String[]{username, null};
        }
        return new String[]{username.substring(0, pos), username.substring(pos + 1)};
    }
}
