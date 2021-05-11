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
import javax.inject.Singleton;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.ldap.LdapContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Named
@Singleton
public class ConcordLdapContextFactory implements LdapContextFactory {

    private final LdapContextFactory delegate;

    private final String ldapUrl;

    private static final Logger log = LoggerFactory.getLogger(ConcordLdapContextFactory.class);

    private static final String protocol = "ldaps";
    private static final String port = "3269";

    @Inject
    @SuppressWarnings("unchecked")
    public ConcordLdapContextFactory(LdapConfiguration cfg) throws NamingException {
        JndiLdapContextFactory f = new JndiLdapContextFactory() {
            @Override
            protected LdapContext createLdapContext(Hashtable env) throws NamingException {
                String url = cfg.getUrl();
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

        if (cfg.getDnsSRVName() != null) {
            List<String> ldapServers = getLdapServers(cfg.getDnsSRVName());
            for (String ldapServer : ldapServers) {
                f.setUrl(ldapServer);
                try {
                    f.getSystemLdapContext();
                } catch (Exception e) {
                    log.warn("failed to connect ldap server: ['{}'], error: {}", ldapServer, e.getMessage());
                    continue;
                }
                log.info("Connected to ldap server: " + ldapServer);
                break;
            }
        } else {
            f.setUrl(cfg.getUrl());
        }
        this.delegate = f;
        this.ldapUrl = f.getUrl();
    }

    public String getLdapUrl() {
        return ldapUrl;
    }

    @Override
    public LdapContext getSystemLdapContext() throws NamingException {
        return delegate.getSystemLdapContext();
    }

    @Override
    public LdapContext getLdapContext(String username, String password) throws NamingException {
        return delegate.getLdapContext(username, password);
    }

    @Override
    public LdapContext getLdapContext(Object principal, Object credentials) throws NamingException {
        return delegate.getLdapContext(principal, credentials);
    }

    private static List<String> getLdapServers(String dnsSRVName) throws NamingException {
        List<String> servers = new ArrayList<>();
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

    public static String removeLastCharIfDot(String s) {
        if (s == null || s.length() == 0)
            return null;
        if (s.charAt(s.length() - 1) != '.')
            return s;
        return s.substring(0, s.length() - 1);
    }
}
