package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcordDnsSrvLdapContextFactory implements LdapContextFactory {

    private LdapContextFactory delegate;

    private static final Logger log = LoggerFactory.getLogger(ConcordDnsSrvLdapContextFactory.class);

    private static final String PROTOCOL = "ldaps";
    private static final String PORT = "3269";
    private static final int MAX_RETRY = 100;

    private final LdapConfiguration cfg;
    private final Lock mutex = new ReentrantLock();

    private Iterator<String> ldapUrlIterator;

    /*
    1. check dnsSRV and refresh SRV list
    2. pic one and create LDAP context
    3. while getContext check for communication exception and update context with next URL in SRV list
    4. on SRV list empty, call step 1.
     */
    public ConcordDnsSrvLdapContextFactory(LdapConfiguration cfg) {
        this.cfg = cfg;
        this.setLdapContextFactory(getNewContextInstance(resolveUrl()));
    }

    public String getCurrentLdapUrl() {
        return ((JndiLdapContextFactory) this.delegate).getUrl();
    }

    public void setLdapContextFactory(LdapContextFactory ldapContextFactory) {
        mutex.lock();
        try {
            this.delegate = ldapContextFactory;
        } finally {
            mutex.unlock();
        }
    }

    public void setLdapUrlIterator(Iterator<String> ldapUrlIterator) {
        mutex.lock();
        try {
            this.ldapUrlIterator = ldapUrlIterator;
        } finally {
            mutex.unlock();
        }
    }

    @Override
    public LdapContext getSystemLdapContext() {
        try {
            return withRetry(() -> this.delegate.getSystemLdapContext());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LdapContext getLdapContext(Object principal, Object credentials) {
        try {
            return withRetry(() -> this.delegate.getLdapContext(principal, credentials));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T withRetry(Callable<T> f) throws Exception {
        int tryCount = 0;
        while (tryCount < MAX_RETRY) {
            try {
                return f.call();
            } catch (CommunicationException e) {
                handleCommunicationException(e);
            }
            tryCount++;
        }
        throw new IllegalStateException("Too deep, max retries limit reached with ldap url identifier");
    }

    public void refreshSRVList() throws NamingException {
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
                if (url != null && url.startsWith(PROTOCOL) && cfg.isTrustAllCertificates()) {
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
                servers.add(PROTOCOL + "://" + removeLastCharIfDot(attr.split(" ")[3]) + ":" + PORT);
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
            String ldapUrl = getNextLdapUrl();
            if (ldapUrl != null) {
                return ldapUrl;
            }
        }
        if (cfg.getUrl() != null) {
            return cfg.getUrl();
        }
        return null;
    }

    private String getNextLdapUrl() {
        mutex.lock();
        try {
            if (ldapUrlIterator != null && ldapUrlIterator.hasNext()) {
                return ldapUrlIterator.next();
            }
            return null;
        } finally {
            mutex.unlock();
        }
    }

    private void handleCommunicationException(Exception e) throws NamingException {
        if (cfg.getDnsSRVName() == null) {
            log.error("Failed to communicate with ldap server: " + getCurrentLdapUrl());
            throw new RuntimeException(e);
        }

        if (this.ldapUrlIterator != null && !this.ldapUrlIterator.hasNext()) {
            this.refreshSRVList();
        }

        this.setLdapContextFactory(getNewContextInstance(getNextLdapUrl()));
    }
}
