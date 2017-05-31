package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.*;

@Named
@Singleton
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

    @WithTimer
    public LdapInfo getInfo(String username) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = ctxFactory.getSystemLdapContext();
            return getInfo(ctx, username);
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    @WithTimer
    public LdapInfo getInfo(LdapContext ctx, String username) throws NamingException {
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        Object[] args = new Object[]{username};

        NamingEnumeration answer = ctx.search(cfg.getSearchBase(), cfg.getPrincipalSearchFilter(), args, searchCtls);
        if (!answer.hasMoreElements()) {
            return null;
        }

        LdapInfoBuilder b = new LdapInfoBuilder(username);
        while (answer.hasMoreElements()) {
            SearchResult sr = (SearchResult) answer.next();

            Attributes attrs = sr.getAttributes();
            if (attrs != null) {
                NamingEnumeration ae = attrs.getAll();
                while (ae.hasMore()) {
                    Attribute attr = (Attribute) ae.next();
                    processAttribute(b, attr);
                }
            }
        }
        return b.build();
    }

    private void processAttribute(LdapInfoBuilder b, Attribute attr) throws NamingException {
        String id = attr.getID();
        switch (id) {
            case MEMBER_OF_ATTR: {
                Collection<String> names = LdapUtils.getAllAttributeValues(attr);
                b.addGroups(names);
                break;
            }
            case DISPLAY_NAME_ATTR: {
                b.displayName(attr.get().toString());
                break;
            }
            default: {
                Set<String> exposedAttr = cfg.getExposeAttributes();
                if (exposedAttr == null || exposedAttr.isEmpty() || exposedAttr.contains(id)) {
                    b.addAttribute(id, attr.get().toString());
                }
            }
        }
    }

    private static final class LdapInfoBuilder {

        private final String username;
        private String displayName;
        private Set<String> groups;
        private Map<String, String> attributes;

        private LdapInfoBuilder(String username) {
            this.username = username;
        }

        public LdapInfoBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public LdapInfoBuilder addGroups(Collection<String> names) {
            if (groups == null) {
                groups = new HashSet<>();
            }
            groups.addAll(names);
            return this;
        }

        public LdapInfoBuilder addAttribute(String k, String v) {
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put(k, v);
            return this;
        }

        public LdapInfo build() {
            if (groups == null) {
                groups = Collections.emptySet();
            }
            if (attributes == null) {
                attributes = Collections.emptyMap();
            }

            return new LdapInfo(username, displayName, groups, attributes);
        }
    }
}
