package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.security.ConcordShiroAuthorizer;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.ldap.AbstractLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import java.util.Arrays;
import java.util.UUID;

@Named
public class LdapRealm extends AbstractLdapRealm {

    private final LdapConfiguration cfg;
    private final UserDao userDao;
    private final ConcordShiroAuthorizer authorizer;

    @Inject
    public LdapRealm(LdapConfiguration cfg, UserDao userDao, ConcordShiroAuthorizer authorizer) {
        this.cfg = cfg;
        this.userDao = userDao;
        this.authorizer = authorizer;

        this.url = cfg.getUrl(); // "ldap://honts0102.homeoffice.wal-mart.com:389";
        this.searchBase = cfg.getSearchBase(); //"DC=homeoffice,DC=Wal-Mart,DC=com";
        this.principalSuffix = cfg.getPrincipalSuffix(); // "@homeoffice.wal-mart.com";
        this.systemUsername = cfg.getSystemUsername(); // "gec-svcmaven";
        this.systemPassword = cfg.getSystemPassword(); // "Maven123";

        setCachingEnabled(false);

        setCredentialsMatcher((token, info) -> {
            SimpleAccount a = (SimpleAccount) info;

            UsernamePasswordToken stored = (UsernamePasswordToken) a.getCredentials();
            UsernamePasswordToken received = (UsernamePasswordToken) token;

            return stored.getUsername().equals(received.getUsername()) &&
                    stored.getPassword().equals(received.getPassword());
        });
    }

    @Override
    protected AuthenticationInfo queryForAuthenticationInfo(AuthenticationToken token, LdapContextFactory ldapContextFactory) throws NamingException {
        UsernamePasswordToken t = (UsernamePasswordToken) token;
        String username = t.getUsername();

        LdapContext ctx = null;
        try {
            ctx = ldapContextFactory.getLdapContext(username, new String(t.getPassword()));
        } finally {
            LdapUtils.closeContext(ctx);
        }

        UserEntry u;
        String id = userDao.getId(username);
        if (id == null) {
            // TODO move into UserManager
            id = UUID.randomUUID().toString();
            userDao.insert(id, username, null);

            u = new UserEntry(id, username, null);
        } else {
            u = userDao.get(id);
        }

        // TODO group to roles?
        return new SimpleAccount(Arrays.asList(u, t), t, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        UserEntry u = (UserEntry) principals.getPrimaryPrincipal();
        return authorizer.getAuthorizationInfo(u);
    }

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principal, LdapContextFactory ldapContextFactory) throws NamingException {
        throw new IllegalStateException("Not supported");
    }
}
