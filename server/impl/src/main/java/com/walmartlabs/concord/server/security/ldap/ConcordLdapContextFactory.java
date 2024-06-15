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

import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;

public class ConcordLdapContextFactory implements LdapContextFactory {
    private final LdapContextFactory delegate;

    @SuppressWarnings("unchecked")
    public ConcordLdapContextFactory(LdapConfiguration cfg) {
        JndiLdapContextFactory f = new JndiLdapContextFactory() {
            @Override
            protected LdapContext createLdapContext(Hashtable env) throws NamingException {
                String url = cfg.getUrl();
                if (url != null && url.startsWith("ldaps:")) {
                    env.put("java.naming.ldap.factory.socket", TrustingSslSocketFactory.class.getName());
                }

                env.put("com.sun.jndi.ldap.read.timeout", Long.toString(cfg.getConnectTimeout().toMillis()));
                env.put("com.sun.jndi.ldap.connect.timeout", Long.toString(cfg.getReadTimeout().toMillis()));
                return super.createLdapContext(env);
            }
        };
        f.setUrl(cfg.getUrl());
        f.setSystemUsername(cfg.getSystemUsername());
        f.setSystemPassword(cfg.getSystemPassword());
        f.setPoolingEnabled(true);
        this.delegate = f;
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

}
