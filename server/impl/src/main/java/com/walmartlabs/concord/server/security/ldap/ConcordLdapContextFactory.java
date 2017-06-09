package com.walmartlabs.concord.server.security.ldap;

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.ldap.LdapContextFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;

@Named
public class ConcordLdapContextFactory implements LdapContextFactory {

    private final LdapContextFactory delegate;

    @Inject
    public ConcordLdapContextFactory(LdapConfiguration cfg) {
        JndiLdapContextFactory f = new JndiLdapContextFactory() {
            @Override
            protected LdapContext createLdapContext(Hashtable env) throws NamingException {
                String url = cfg.getUrl();
                if (url != null && url.startsWith("ldaps:")) {
                    env.put("java.naming.ldap.factory.socket", TrustingSslSocketFactory.class.getName());
                }
                return super.createLdapContext(env);
            }
        };

        f.setUrl(cfg.getUrl());
        f.setSystemUsername(cfg.getSystemUsername());
        f.setSystemPassword(cfg.getSystemPassword());

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
