package com.walmartlabs.concord.server.cfg;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class SsoConfiguration implements Serializable {

    @Inject
    @Config("sso.enabled")
    private boolean enabled;

    @Inject
    @Config("sso.authEndpointUrl")
    private String authEndPointUrl;

    @Inject
    @Config("sso.tokenEndpointUrl")
    private String tokenEndPointUrl;

    @Inject
    @Config("sso.logoutEndpointUrl")
    private String logoutEndpointUrl;

    @Inject
    @Config("sso.redirectUrl")
    private String redirectUrl;

    @Inject
    @Config("sso.clientId")
    private String clientId;

    @Inject
    @Config("sso.clientSecret")
    private String clientSecret;

    private Path tokenSigningKey;

    private Path tokenEncryptionKey;

    @Inject
    @Config("sso.tokenServiceReadTimeout")
    private int tokenServiceReadTimeout;

    @Inject
    @Config("sso.tokenServiceConnectTimeout")
    private int tokenServiceConnectTimeout;

    @Inject
    @Config("sso.validateNonce")
    private boolean validateNonce;

    @Inject
    public SsoConfiguration(@Config("sso.tokenSigningKey") @Nullable String tokenSigningKeyFile,
                            @Config("sso.tokenEncryptionKey") @Nullable String tokenEncryptionKeyFile) {
        this.tokenSigningKey = tokenSigningKeyFile != null ? Paths.get(tokenSigningKeyFile) : null;
        this.tokenEncryptionKey = tokenEncryptionKeyFile != null ? Paths.get(tokenEncryptionKeyFile) : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getAuthEndPointUrl() {
        return authEndPointUrl;
    }

    public String getTokenEndPointUrl() {
        return tokenEndPointUrl;
    }

    public String getLogoutEndpointUrl() {
        return logoutEndpointUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public Path getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public Path getTokenSigningKey() {
        return tokenSigningKey;
    }

    public int getTokenServiceReadTimeout() {
        return tokenServiceReadTimeout;
    }

    public int getTokenServiceConnectTimeout() {
        return tokenServiceConnectTimeout;
    }

    public boolean isValidateNonce() {
        return validateNonce;
    }
}
