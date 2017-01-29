package com.walmartlabs.concord.server.security;

import com.google.inject.Key;
import com.walmartlabs.concord.server.security.apikey.ApiKeyFilter;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import org.apache.shiro.guice.web.ShiroWebModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

public class SecurityModule extends ShiroWebModule {

    private static final Logger log = LoggerFactory.getLogger(SecurityModule.class);

    public SecurityModule(ServletContext servletContext) {
        super(servletContext);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void configureShiroWeb() {
        bindRealm().to(ApiKeyRealm.class);

        addFilterChain("/api/v1/server/ping", ANON);
        addFilterChain("/api/**", NO_SESSION_CREATION, Key.get(ApiKeyFilter.class));
    }
}
