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

import com.walmartlabs.ollie.config.Config;
import org.eclipse.sisu.Nullable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;

@Named
@Singleton
public class PluginConfiguration {

    @Inject
    @Config("oidc.enabled")
    private boolean enabled;

    @Inject
    @Config("oidc.clientId")
    private String clientId;

    @Inject
    @Config("oidc.secret")
    private String secret;

    @Inject
    @Config("oidc.discoveryUri")
    private String discoveryUri;

    @Inject
    @Config("oidc.urlBase")
    private String urlBase;

    @Inject
    @Config("oidc.afterLoginUrl")
    private String afterLoginUrl;

    @Inject
    @Config("oidc.afterLogoutUrl")
    private String afterLogoutUrl;

    @Inject
    @Nullable
    @Config("oidc.roles")
    private List<String> roles;

    public boolean isEnabled() {
        return enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSecret() {
        return secret;
    }

    public String getDiscoveryUri() {
        return discoveryUri;
    }

    public String getUrlBase() {
        return urlBase;
    }

    public String getAfterLoginUrl() {
        return afterLoginUrl;
    }

    public String getAfterLogoutUrl() {
        return afterLogoutUrl;
    }

    public List<String> getRoles() {
        return roles;
    }
}
