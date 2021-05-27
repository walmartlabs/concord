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
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Named
public class ConcordLdapContextFactory implements LdapContextFactory {

    private LdapContextFactory delegate;

    private static final Logger log = LoggerFactory.getLogger(ConcordLdapContextFactory.class);

    private static final String protocol = "ldaps";
    private static final String port = "3269";
    private static final int MAX_DEPTH = 20;

    private Iterator<String> ldapUrlIterator;

    private final LdapConfiguration cfg;

    /*
    1. check dnsSRV and refresh SRV list
    2. pic one and create LDAP context
    3. while getContext check for communication exception and update context with next URL in SRV list
    4. on SRV list empty, call step 1.
     */
    @Inject
    @SuppressWarnings("unchecked")
    public ConcordLdapContextFactory(LdapConfiguration cfg) throws NamingException {
        this.cfg = cfg;

        this.refreshSRVList();

        this.setLdapContextFactory(getNewContextInstance(resolveUrl()));
    }

    public String getCurrentLdapUrl() {
        return ((JndiLdapContextFactory) this.delegate).getUrl();
    }

    public synchronized void setLdapContextFactory(LdapContextFactory ldapContextFactory) {
        this.delegate = ldapContextFactory;
    }

    public synchronized void setLdapUrlIterator(Iterator<String> ldapUrlIterator) {
        this.ldapUrlIterator = ldapUrlIterator;
    }

    @Override
    public LdapContext getSystemLdapContext() throws NamingException {
        return getSystemLdapContext(0);
    }

    private LdapContext getSystemLdapContext(int depth) throws NamingException {
        try {
            return this.delegate.getSystemLdapContext();
        } catch (CommunicationException e) {
            while (depth < MAX_DEPTH) {
                depth++;
                handleCommunicationException(e);
                return getSystemLdapContext(depth);
            }
            throw new IllegalStateException("Too deep, recursion limit reached, emerging");
        }
    }

    @Override
    public LdapContext getLdapContext(String username, String password) throws NamingException {
        return getLdapContext(username, password, 0);
    }

    private LdapContext getLdapContext(String username, String password, int depth) throws NamingException {
        try {
            return this.delegate.getLdapContext(username, password);
        } catch (CommunicationException e) {
            while (depth < MAX_DEPTH) {
                depth++;
                handleCommunicationException(e);
                return getLdapContext(username, password, depth);
            }
            throw new IllegalStateException("Too deep, recursion limit reached, emerging");
        }
    }

    @Override
    public LdapContext getLdapContext(Object principal, Object credentials) throws NamingException {
        return getLdapContext(principal, credentials, 0);
    }

    private LdapContext getLdapContext(Object principal, Object credentials, int depth) throws NamingException {
        try {
            return this.delegate.getLdapContext(principal, credentials);
        } catch (CommunicationException e) {
            while (depth < MAX_DEPTH) {
                depth++;
                handleCommunicationException(e);
                return getLdapContext(principal, credentials, depth);
            }
            throw new IllegalStateException("Too deep, recursion limit reached, emerging");
        }
    }

    private void refreshSRVList() throws NamingException {
        if (cfg.getDnsSRVName() != null) {
            this.setLdapUrlIterator(getLdapServers(cfg.getDnsSRVName()).iterator());
        }
    }

    private JndiLdapContextFactory getNewContextInstance(String ldapUrl) {
        if (ldapUrl == null || ldapUrl.isEmpty()) {
            log.error("LDAP Url is null or empty");
            throw new RuntimeException("An LDAP URL must be specified of the form ldap://<hostname>:<port>");
        }
        JndiLdapContextFactory f = new JndiLdapContextFactory() {
            @Override
            protected LdapContext createLdapContext(Hashtable env) throws NamingException {
                String url = getCurrentLdapUrl();
                if (url != null && url.startsWith(protocol)) {
                    env.put("java.naming.ldap.factory.socket", TrustingSslSocketFactory.class.getName());
                }

                env.put("com.sun.jndi.ldap.read.timeout", Long.toString(cfg.getConnectTimeout().toMillis()));
                env.put("com.sun.jndi.ldap.connect.timeout", Long.toString(cfg.getReadTimeout().toMillis()));

                return super.createLdapContext(env);
            }
        };

        f.setSystemUsername(cfg.getSystemUsername());
        f.setSystemPassword(cfg.getSystemPassword());
        f.setPoolingEnabled(true);
        log.info("Connecting to ldap server: " + ldapUrl);
        f.setUrl(ldapUrl);
        return f;
    }

    private List<String> getLdapServers(String dnsSRVName) throws NamingException {
        CopyOnWriteArrayList<String> servers = new CopyOnWriteArrayList<>();
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put(Context.PROVIDER_URL, "dns:");
        DirContext ctx = new InitialDirContext(env);
        Attributes srvs = ctx.getAttributes(dnsSRVName, new String[]{"SRV"});
        NamingEnumeration<? extends Attribute> srv = srvs.getAll();
        while (srv.hasMore()) {
            Attribute srvRecords = (Attribute) srv.next();
            NamingEnumeration<?> srvRecord = srvRecords.getAll();
            while (srvRecord.hasMore()) {
                String attr = (String) srvRecord.next();
                servers.add(protocol + "://" + removeLastCharIfDot(attr.split(" ")[3]) + ":" + port);
            }
        }
        return servers;
    }

    private static String removeLastCharIfDot(String s) {
        if (s == null || s.length() == 0 || s.charAt(s.length() - 1) != '.') {
            return s;
        }
        return s.substring(0, s.length() - 1);
    }

    private String resolveUrl() {
        if (cfg.getDnsSRVName() != null) {
            String ldapUrl = getNextLdapUrl(this.ldapUrlIterator);
            if (ldapUrl != null) {
                return ldapUrl;
            }
        }
        if (cfg.getUrl() != null) {
            return cfg.getUrl();
        }
        return null;
    }

    private static String getNextLdapUrl(Iterator<String> ldapUrlIterator) {
        if (ldapUrlIterator != null && ldapUrlIterator.hasNext()) {
            return ldapUrlIterator.next();
        }
        return null;
    }

    private void handleCommunicationException(Exception e) throws NamingException {
        if (cfg.getDnsSRVName() == null) {
            log.error("Failed to communicate with ldap server: " + getCurrentLdapUrl());
            throw new RuntimeException(e);
        }

        if (this.ldapUrlIterator != null && !this.ldapUrlIterator.hasNext()) {
            this.refreshSRVList();
        }
        this.setLdapContextFactory(getNewContextInstance(getNextLdapUrl(this.ldapUrlIterator)));
    }
}
