package com.walmartlabs.concord.server.security;

import com.google.inject.Key;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import com.walmartlabs.concord.server.security.github.GithubRealm;
import com.walmartlabs.concord.server.security.ldap.LdapRealm;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyRealm;
import org.apache.shiro.guice.web.ShiroWebModule;

import javax.servlet.ServletContext;

public class SecurityModule extends ShiroWebModule {

    public SecurityModule(ServletContext servletContext) {
        super(servletContext);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void configureShiroWeb() {
        bindRealm().to(ApiKeyRealm.class);
        bindRealm().to(LdapRealm.class);
        bindRealm().to(SessionKeyRealm.class);
        bindRealm().to(GithubRealm.class);

        addFilterChain("/api/**", Key.get(ConcordAuthenticatingFilter.class));
        addFilterChain("/forms/**", Key.get(ConcordAuthenticatingFilter.class));
        addFilterChain("/jolokia/**", Key.get(ConcordAuthenticatingFilter.class));
        addFilterChain("/events/github/**", Key.get(GithubAuthenticatingFilter.class));
    }
}
