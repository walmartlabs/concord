package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import com.google.inject.Provides;
import com.walmartlabs.concord.server.boot.FilterChainConfigurator;
import com.walmartlabs.concord.server.boot.filters.AuthenticationHandler;
import org.apache.shiro.realm.Realm;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.engine.DefaultCallbackLogic;
import org.pac4j.core.engine.DefaultLogoutLogic;
import org.pac4j.core.http.adapter.JEEHttpActionAdapter;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

@Named
public class OidcPluginModule extends AbstractModule {

    public static final String CLIENT_NAME = "oidc";

    @Override
    protected void configure() {
        newSetBinder(binder(), AuthenticationHandler.class).addBinding().to(OidcAuthenticationHandler.class);
        newSetBinder(binder(), FilterChainConfigurator.class).addBinding().to(OidcFilterChainConfigurator.class);
        newSetBinder(binder(), Realm.class).addBinding().to(OidcRealm.class);
    }

    @Provides
    public OidcConfiguration oidcConfiguration(PluginConfiguration cfg) {
        OidcConfiguration oidcCfg = new OidcConfiguration();
        oidcCfg.setClientId(cfg.getClientId());
        oidcCfg.setSecret(cfg.getSecret());
        oidcCfg.setDiscoveryURI(cfg.getDiscoveryUri());
        if (cfg.getScopes() != null) {
            oidcCfg.setScope(String.join(" ", cfg.getScopes()));
        }
        return oidcCfg;
    }

    @Provides
    @Singleton
    public OidcClient<?> oidcClient(PluginConfiguration cfg, OidcConfiguration oidcCfg) {
        OidcClient<?> client = new OidcClient<>(oidcCfg);
        client.setName(CLIENT_NAME);
        client.setCallbackUrl(cfg.getUrlBase() + OidcCallbackFilter.URL);
        return client;
    }

    @Provides
    @Named("oidc")
    public Config pac4jConfig(OidcClient<?> client) {
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
