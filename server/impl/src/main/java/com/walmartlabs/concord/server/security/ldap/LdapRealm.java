package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.security.ConcordShiroAuthorizer;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.ldap.AbstractLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

@Named
public class LdapRealm extends AbstractLdapRealm {

    private final UserDao userDao;
    private final LdapDao ldapDao;
    private final ConcordShiroAuthorizer authorizer;
    private final String usernameSuffix;
    private final String userFilter;

    @Inject
    public LdapRealm(LdapConfiguration cfg, UserDao userDao, LdapDao ldapDao, ConcordShiroAuthorizer authorizer) {
        this.userDao = userDao;
        this.ldapDao = ldapDao;
        this.authorizer = authorizer;

        this.url = cfg.getUrl();
        this.searchBase = cfg.getSearchBase();
        this.usernameSuffix = cfg.getPrincipalSuffix();
        this.userFilter = cfg.getPrincipalSearchFilter();
        this.systemUsername = cfg.getSystemUsername();
        this.systemPassword = cfg.getSystemPassword();

        setCachingEnabled(false);

        setCredentialsMatcher((token, info) -> {
            SimpleAccount a = (SimpleAccount) info;

            UsernamePasswordToken stored = (UsernamePasswordToken) a.getCredentials();
            UsernamePasswordToken received = (UsernamePasswordToken) token;

            return stored.getUsername().equals(received.getUsername()) &&
                    Arrays.equals(stored.getPassword(), received.getPassword());
        });
    }

    @Override
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
        } finally {
            LdapUtils.closeContext(ctx);
        }

        UserEntry u;

        String id = userDao.getId(username);
        if (id == null) {
            u = new UserEntry("ldap", username, null);
        } else {
            u = userDao.get(id);
        }

        return new SimpleAccount(Arrays.asList(u, t), t, getName());
    }

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals, LdapContextFactory ldapContextFactory) throws NamingException {
        UserEntry u = (UserEntry) principals.getPrimaryPrincipal();
        Collection<String> roles = new HashSet<>();

        LdapContext ctx = null;
        try {
            ctx = ldapContextFactory.getSystemLdapContext();

            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            Object[] args = new Object[]{u.getName()};

            NamingEnumeration answer = ctx.search(searchBase, userFilter, args, searchCtls);

            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                Attributes attrs = sr.getAttributes();

                if (attrs != null) {
                    NamingEnumeration ae = attrs.getAll();
                    while (ae.hasMore()) {
                        Attribute attr = (Attribute) ae.next();

                        if (attr.getID().equals("memberOf")) {
                            Collection<String> groupNames = LdapUtils.getAllAttributeValues(attr);
                            roles.addAll(ldapDao.getRoles(groupNames));
                        }
                    }
                }
            }

            return authorizer.getAuthorizationInfo(u, roles);
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }
}
