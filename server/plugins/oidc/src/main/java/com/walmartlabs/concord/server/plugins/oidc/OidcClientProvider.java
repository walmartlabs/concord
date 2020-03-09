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

import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Named
@Singleton
public class OidcClientProvider implements Provider<OidcClient<?>> {

    public static final String NAME = "oidc";

    private final PluginConfiguration cfg;

    @Inject
    public OidcClientProvider(PluginConfiguration cfg) {
        this.cfg = cfg;
    }

    @Override
    public OidcClient<?> get() {
        OidcConfiguration oidcCfg = new OidcConfiguration();
        oidcCfg.setClientId(cfg.getClientId());
        oidcCfg.setSecret(cfg.getSecret());
        oidcCfg.setDiscoveryURI(cfg.getDiscoveryUri());

        OidcClient<?> client = new OidcClient<>(oidcCfg);
        client.setName(NAME);
        client.setCallbackUrl(cfg.getUrlBase() + OidcCallbackFilter.URL);
        return client;
    }
}
