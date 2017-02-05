package com.walmartlabs.concord.server;

import com.google.inject.servlet.ServletModule;
import com.walmartlabs.concord.common.bootstrap.BootstrapModule;
import com.walmartlabs.concord.common.db.DatabaseModule;
import org.apache.shiro.guice.web.ShiroWebModule;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class ServerModule extends BootstrapModule {

    @Override
    protected void configureAdditionalModules() {
        install(new DatabaseModule());
    }

    @Override
    protected ServletModule configureAdditionalServlets() {
        return new ServletModule() {
            @Override
            protected void configureServlets() {
                filter("/api/*", "/logs/*", "/swagger/*").through(CORSFilter.class);
                ShiroWebModule.bindGuiceFilter(binder());
            }
        };
    }
}
