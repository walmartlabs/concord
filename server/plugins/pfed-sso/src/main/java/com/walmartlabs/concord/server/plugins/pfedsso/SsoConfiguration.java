package com.walmartlabs.concord.server.plugins.pfedsso;

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
import java.io.Serializable;
import java.time.Duration;
import java.util.List;

public class SsoConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Config("sso.pfed.enabled")
    private boolean enabled;

    @Inject
    @Config("sso.pfed.priority")
    private int priority;

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

    @Inject
    @Config("sso.pfed.bearerToken.enableBearerTokens")
    private boolean enableBearerTokens;

    @Inject
    @Config("sso.pfed.bearerToken.allowAllClientIds")
    private boolean allowAllClientIds;

    @Inject
    @Nullable
    @Config("sso.tokenSigningKey")
    private String tokenSigningKey;

    @Inject
    @Nullable
    @Config("sso.tokenEncryptionKey")
    private String tokenEncryptionKey;

    @Inject
    @Config("sso.tokenServiceReadTimeout")
    private Duration tokenServiceReadTimeout;

    @Inject
    @Config("sso.tokenServiceConnectTimeout")
    private Duration tokenServiceConnectTimeout;

    @Inject
    @Config("sso.validateNonce")
    private boolean validateNonce;

    @Inject
    @Nullable
    @Config("sso.tokenSigningKeyUrl")
    private String tokenSigningKeyUrl;

    @Inject
    @Config("sso.tokenSignatureValidation")
    private boolean tokenSignatureValidation;

    @Inject
    @Nullable
    @Config("sso.userInfoEndpointUrl")
    private String userInfoEndpointUrl;

    @Inject
    @Config("sso.autoCreateUsers")
    private boolean autoCreateUsers;

    @Inject
    @Config("sso.pfed.bearerToken.allowedClientIds")
    private List<String> allowedClientIds;

    public boolean isAutoCreateUsers() {
        return autoCreateUsers;
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

    public boolean getEnableBearerTokens() {
        return enableBearerTokens;
    }

    public boolean getAllowAllClientIds() {
        return allowAllClientIds;
    }

    public String getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public String getTokenSigningKey() {
        return tokenSigningKey;
    }

    public Duration getTokenServiceReadTimeout() {
        return tokenServiceReadTimeout;
    }

    public Duration getTokenServiceConnectTimeout() {
        return tokenServiceConnectTimeout;
    }

    public boolean isValidateNonce() {
        return validateNonce;
    }

    public int getPriority() {
        return priority;
    }

    public String getTokenSigningKeyUrl() {
        return tokenSigningKeyUrl;
    }

    public boolean isTokenSignatureValidation() {
        return tokenSignatureValidation;
    }

    public String getUserInfoEndpointUrl() {
        return userInfoEndpointUrl;
    }

    public List<String> getAllowedClientIds() {
        return allowedClientIds;
    }

}
