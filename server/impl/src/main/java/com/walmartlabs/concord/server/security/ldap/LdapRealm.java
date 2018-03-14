package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationException;
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

    private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);
    public static final String REALM_NAME = "ldap";

    private final UserManager userManager;
    private final LdapManager ldapManager;

    private final String usernameSuffix;

    @Inject
    public LdapRealm(LdapConfiguration cfg,
                     UserManager userManager,
                     ConcordLdapContextFactory ctxFactory,
                     LdapManager ldapManager) {

        this.userManager = userManager;
        this.ldapManager = ldapManager;

        this.url = cfg.getUrl();
        this.searchBase = cfg.getSearchBase();
        this.usernameSuffix = cfg.getPrincipalSuffix();
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
        UsernamePasswordToken t = (UsernamePasswordToken) token;

        String username = t.getUsername();
        char[] password = t.getPassword();
        if (username == null || password == null) {
            return null;
        }

        if (username.contains("\\")) {
            throw new AuthenticationException("User's domain should be specified as username@domain");
        }

        String principalName = username;
        if (!principalName.contains("@")) {
            principalName = principalName + usernameSuffix;
        }

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

        username = normalizeUsername(username);

        LdapInfo ldapInfo = ldapManager.getInfo(username);
        if (ldapInfo == null) {
            throw new AuthenticationException("LDAP data not found: " + username);
        }

        UserEntry user = userManager.getOrCreate(username, UserType.LDAP);
        UserPrincipal p = new UserPrincipal(REALM_NAME, user, ldapInfo);

        return new SimpleAccount(Arrays.asList(p, t), t, getName());
    }

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals, LdapContextFactory ldapContextFactory) {
        UserPrincipal p = (UserPrincipal) principals.getPrimaryPrincipal();
        if (!"ldap".equals(p.getRealm())) {
            return null;
        }

        LdapInfo i = p.getLdapInfo();
        if (i == null) {
            throw new AuthorizationException("LDAP data not found: " + p.getUsername());
        }

        return new SimpleAuthorizationInfo();
    }

    private static String normalizeUsername(String s) {
        if (s == null) {
            return s;
        }

        int i = s.indexOf("@");
        if (i < 0) {
            return s;
        }

        return s.substring(0, i);
    }
}
