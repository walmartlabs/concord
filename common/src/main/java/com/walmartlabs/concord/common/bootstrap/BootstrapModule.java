package com.walmartlabs.concord.common.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.servlet.ServletModule;
import org.sonatype.siesta.server.SiestaServlet;
import org.sonatype.siesta.server.resteasy.ResteasyModule;
import org.sonatype.siesta.server.validation.ValidationModule;

import java.util.Collections;

public class BootstrapModule extends AbstractModule {

    @Override
    protected final void configure() {
        install(new ResteasyModule());
        install(new ValidationModule());

        configureAdditionalModules();

        install(new ServletModule() {
            @Override
            protected void configureServlets() {
                serve("/api/*").with(SiestaServlet.class, Collections.singletonMap("resteasy.servlet.mapping.prefix", "/"));

                ServletModule m = configureAdditionalServlets();
                if (m != null) {
                    install(m);
                }
            }
        });
    }

    protected void configureAdditionalModules() {
    }

    protected ServletModule configureAdditionalServlets() {
        return null;
    }
}
