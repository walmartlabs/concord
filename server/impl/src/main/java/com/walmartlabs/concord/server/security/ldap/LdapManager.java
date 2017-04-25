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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Named
public class LdapManager {

    private static final String MEMBER_OF_ATTR = "memberOf";
    private static final String DISPLAY_NAME_ATTR = "displayName";

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

    public LdapInfo getInfo(String username) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = ctxFactory.getSystemLdapContext();
            return getInfo(ctx, username);
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    public LdapInfo getInfo(LdapContext ctx, String username) throws NamingException {
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        Object[] args = new Object[]{username};

        NamingEnumeration answer = ctx.search(cfg.getSearchBase(), cfg.getPrincipalSearchFilter(), args, searchCtls);

        LdapInfoBuilder b = new LdapInfoBuilder();
        while (answer.hasMoreElements()) {
            SearchResult sr = (SearchResult) answer.next();

            Attributes attrs = sr.getAttributes();
            if (attrs != null) {
                NamingEnumeration ae = attrs.getAll();
                while (ae.hasMore()) {
                    Attribute attr = (Attribute) ae.next();

                    String id = attr.getID();
                    switch (id) {
                        case MEMBER_OF_ATTR: {
                            Collection<String> names = LdapUtils.getAllAttributeValues(attr);
                            b.addGroups(names);
                        }
                        case DISPLAY_NAME_ATTR: {
                            b.displayName(attr.get().toString());
                        }
                    }
                }
            }
        }
        return b.build();
    }

    private static final class LdapInfoBuilder {

        private String displayName;
        private Set<String> groups;

        public LdapInfoBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public LdapInfoBuilder addGroups(Collection<String> names) {
            if (this.groups == null) {
                this.groups = new HashSet<>();
            }
            this.groups.addAll(names);
            return this;
        }

        public LdapInfo build() {
            if (groups == null) {
                groups = Collections.emptySet();
            }
            return new LdapInfo(displayName, groups);
        }
    }
}
