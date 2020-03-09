package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2020 Ivan Bodrov
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

import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.engine.DefaultCallbackLogic;
import org.pac4j.core.engine.DefaultLogoutLogic;
import org.pac4j.core.http.adapter.JEEHttpActionAdapter;
import org.pac4j.oidc.client.OidcClient;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Named("oidc")
@Singleton
public class Pac4JConfigProvider implements Provider<Config> {

    private final OidcClient<?> client;

    @Inject
    public Pac4JConfigProvider(OidcClient<?> client) {
        this.client = client;
    }

    @Override
    public Config get() {
        Config config = new Config();
        config.setSessionStore(new JEESessionStore());
        config.setCallbackLogic(new DefaultCallbackLogic<Object, JEEContext>());
        config.setLogoutLogic(new DefaultLogoutLogic<Object, JEEContext>());
        config.setHttpActionAdapter(JEEHttpActionAdapter.INSTANCE);

        Clients clients = new Clients(client);
        config.setClients(clients);

        return config;
    }
}
