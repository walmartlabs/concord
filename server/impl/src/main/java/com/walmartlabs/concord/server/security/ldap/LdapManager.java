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

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.console.UserSearchResult;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(LdapManager.class);

    private static final String MEMBER_OF_ATTR = "memberOf"; // TODO move to cfg
    private static final String DISPLAY_NAME_ATTR = "displayName"; // TODO move to cfg
    private static final String USER_PRINCIPAL_NAME_ATTR = "userPrincipalName"; // TODO move to cfg

    private final LdapConfiguration cfg;
    private final LdapContextFactory ctxFactory;

    @Inject
    public LdapManager(LdapConfiguration cfg, ConcordLdapContextFactory ctxFactory) {
        this.cfg = cfg;
        this.ctxFactory = ctxFactory;
    }

    public List<UserSearchResult> search(String filter) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = ctxFactory.getSystemLdapContext();

            SearchControls searchCtls = new SearchControls();
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchCtls.setReturningAttributes(new String[]{cfg.getUsernameProperty(), DISPLAY_NAME_ATTR});
            searchCtls.setCountLimit(10);
            Object[] args = new Object[]{filter};

            NamingEnumeration answer = ctx.search(cfg.getSearchBase(), cfg.getUserSearchFilter(), args, searchCtls);
            if (!answer.hasMoreElements()) {
                return Collections.emptyList();
            }

            List<UserSearchResult> l = new ArrayList<>();
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                Attributes attrs = sr.getAttributes();
                if (attrs != null) {
                    String username = null;
                    String displayName = null;

                    NamingEnumeration ae = attrs.getAll();
                    while (ae.hasMore()) {
                        Attribute attr = (Attribute) ae.next();
                        String id = attr.getID();
                        if (cfg.getUsernameProperty().equals(id)) {
                            username = attr.get().toString();
                        } else if (DISPLAY_NAME_ATTR.equals(id)) {
                            displayName = attr.get().toString();
                        }
                    }

                    l.add(new UserSearchResult(username, displayName));
                }
            }
            return l;
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    public LdapPrincipal getPrincipal(String username) throws NamingException {
        LdapContext ctx = null;
        try {
            ctx = ctxFactory.getSystemLdapContext();
            return getPrincipal(ctx, username);
        } catch (Exception e) {
            log.warn("getPrincipal ['{}'] -> error while retrieving LDAP data: {}", username, e.getMessage(), e);
            throw e;
        } finally {
            LdapUtils.closeContext(ctx);
        }
    }

    public LdapPrincipal getPrincipal(LdapContext ctx, String username) throws NamingException {
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        Object[] args = new Object[]{username};

        NamingEnumeration answer = ctx.search(cfg.getSearchBase(), cfg.getPrincipalSearchFilter(), args, searchCtls);
        if (!answer.hasMoreElements()) {
            return null;
        }

        LdapPrincipalBuilder b = new LdapPrincipalBuilder();
        while (answer.hasMoreElements()) {
            SearchResult sr = (SearchResult) answer.next();

            b.nameInNamespace(sr.getNameInNamespace());

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

    private void processAttribute(LdapPrincipalBuilder b, Attribute attr) throws NamingException {
        String id = attr.getID();
        if (id.equals(cfg.getUsernameProperty())) {
            b.username(attr.get().toString());
            return;
        }

        if (id.equals(cfg.getMailProperty())) {
            b.email(attr.get().toString());
            b.addAttribute(id, attr.get().toString());
            return;
        }

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
            case USER_PRINCIPAL_NAME_ATTR: {
                b.userPrincipalName(attr.get().toString());
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

    private static final class LdapPrincipalBuilder {

        private String username;
        private String nameInNamespace;
        private String userPrincipalName;
        private String displayName;
        private String email;
        private Set<String> groups;
        private Map<String, String> attributes;

        public LdapPrincipalBuilder username(String username) {
            this.username = username;
            return this;
        }

        public LdapPrincipalBuilder nameInNamespace(String nameInNamespace) {
            this.nameInNamespace = nameInNamespace;
            return this;
        }

        public LdapPrincipalBuilder userPrincipalName(String userPrincipalName) {
            this.userPrincipalName = userPrincipalName;
            return this;
        }

        public LdapPrincipalBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public LdapPrincipalBuilder email(String email) {
            this.email = email;
            return this;
        }

        public LdapPrincipalBuilder addGroups(Collection<String> names) {
            if (groups == null) {
                groups = new HashSet<>();
            }
            groups.addAll(names);
            return this;
        }

        public LdapPrincipalBuilder addAttribute(String k, String v) {
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put(k, v);
            return this;
        }

        public LdapPrincipal build() {
            if (groups == null) {
                groups = Collections.emptySet();
            }
            if (attributes == null) {
                attributes = Collections.emptyMap();
            }

            return new LdapPrincipal(username, nameInNamespace, userPrincipalName, displayName, email, groups, attributes);
        }
    }
}
