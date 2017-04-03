package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.*;

@Named
public class LdapManager {

    private final LdapConfiguration cfg;
    private final LdapContextFactory ctxFactory;

    @Inject
    public LdapManager(LdapConfiguration cfg) {
        this.cfg = cfg;

        JndiLdapContextFactory f = new JndiLdapContextFactory();
        f.setUrl(cfg.getUrl());
        f.setSystemUsername(cfg.getSystemUsername());
        f.setSystemPassword(cfg.getSystemPassword());

        this.ctxFactory = f;
    }

    public List<String> getGroups(String username) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = ctxFactory.getSystemLdapContext();
            return getGroups(ctx, username);
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    public List<String> getGroups(LdapContext ctx, String username) throws NamingException {
        Collection<String> groups = new HashSet<>();

        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        Object[] args = new Object[]{username};

        NamingEnumeration answer = ctx.search(cfg.getSearchBase(), cfg.getPrincipalSearchFilter(), args, searchCtls);

        while (answer.hasMoreElements()) {
            SearchResult sr = (SearchResult) answer.next();
            Attributes attrs = sr.getAttributes();

            if (attrs != null) {
                NamingEnumeration ae = attrs.getAll();
                while (ae.hasMore()) {
                    Attribute attr = (Attribute) ae.next();

                    if (attr.getID().equals("memberOf")) {
                        Collection<String> groupNames = LdapUtils.getAllAttributeValues(attr);
                        groups.addAll(groupNames);
                    }
                }
            }
        }

        List<String> l = new ArrayList<>(groups);
        Collections.sort(l);

        return l;
    }
}
