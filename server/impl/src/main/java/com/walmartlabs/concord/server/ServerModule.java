package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.servlet.ServletModule;
import com.walmartlabs.concord.server.metrics.JolokiaRestrictor;
import com.walmartlabs.concord.server.metrics.MetricsModule;
import org.apache.shiro.guice.web.ShiroWebModule;
import org.jolokia.http.AgentServlet;
import org.sonatype.siesta.server.SiestaServlet;
import org.sonatype.siesta.server.resteasy.ResteasyModule;
import org.sonatype.siesta.server.validation.ValidationModule;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;

@Named
@Singleton
public class ServerModule extends AbstractModule {

    @Override
    protected final void configure() {
        install(new ResteasyModule());
        install(new ValidationModule());

        install(new MetricsModule());
        install(new DatabaseModule());

        install(new ServletModule() {
            @Override
            protected void configureServlets() {
                serve("/api/*", "/events/github/*").with(SiestaServlet.class, Collections.singletonMap("resteasy.servlet.mapping.prefix", "/"));
                install(configureAdditionalServlets());
            }
        });
    }

    private ServletModule configureAdditionalServlets() {
        return new ServletModule() {
            @Override
            protected void configureServlets() {
                filter("/service/*", "/api/*", "/logs/*", "/forms/*", "/swagger/*").through(CORSFilter.class);
                filter("/service/*", "/api/*", "/logs/*", "/forms/*", "/swagger/*").through(NoCacheFilter.class);
                ShiroWebModule.bindGuiceFilter(binder());

                serve("/jolokia/*").with(new AgentServlet(new JolokiaRestrictor()));
            }
        };
    }
}
