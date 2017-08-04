package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.security.ConcordShiroAuthorizer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

@Named
public class LdapRealm extends AbstractLdapRealm {

    private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);
    public static final String REALM_NAME = "ldap";

    private final UserDao userDao;
    private final LdapDao ldapDao;
    private final LdapManager ldapManager;
    private final ConcordShiroAuthorizer authorizer;

    private final String usernameSuffix;

    @Inject
    public LdapRealm(LdapConfiguration cfg,
                     UserDao userDao,
                     LdapDao ldapDao,
                     ConcordLdapContextFactory ctxFactory,
                     LdapManager ldapManager,
                     ConcordShiroAuthorizer authorizer) {

        this.userDao = userDao;
        this.ldapDao = ldapDao;
        this.ldapManager = ldapManager;
        this.authorizer = authorizer;

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

        UUID id = userDao.getId(username);
        UserPrincipal p = new UserPrincipal(REALM_NAME, id, username, ldapInfo);

        return new SimpleAccount(Arrays.asList(p, t), t, getName());
    }

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals, LdapContextFactory ldapContextFactory) throws NamingException {
        UserPrincipal u = (UserPrincipal) principals.getPrimaryPrincipal();

        LdapInfo i = u.getLdapInfo();
        if (i == null) {
            throw new AuthorizationException("LDAP data not found: " + u.getUsername());
        }

        Collection<String> roles = new HashSet<>();
        roles.addAll(ldapDao.getRoles(i.getGroups()));
        return authorizer.getAuthorizationInfo(u, roles);
    }

    private static final String normalizeUsername(String s) {
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
