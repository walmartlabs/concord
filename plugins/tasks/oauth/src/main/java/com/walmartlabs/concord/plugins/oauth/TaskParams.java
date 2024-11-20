package com.walmartlabs.concord.plugins.oauth;

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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.io.Serializable;
import java.util.Map;
import java.util.stream.Collectors;

public class TaskParams {

    private static final String OAUTH_PROVIDER_KEY = "provider";
    private static final String PROVIDER_INFO_KEY = "providerInfo";

    private static final String CLIENT_ID_KEY = "clientId";
    private static final String AUTHORITY_URL_KEY = "authorityUrl";
    private static final String CLIENT_SECRET_KEY = "clientSecret";
    private static final String SCOPE_KEY = "scope";

    private final Variables variables;

    public TaskParams(Variables variables) {
        this.variables = variables;
    }

    public Map<String, Serializable> asMap() {
        return variables.toMap()
                .entrySet()
                .stream()
                .filter(e -> e.getValue() != null)
                .filter(e -> e.getValue() instanceof Serializable)
                .collect(Collectors.toMap(Map.Entry::getKey, o -> (Serializable) o.getValue()));
    }


    public OAuthProvider oAuthProvider() {
        String oAuthProvider = variables.assertString(OAUTH_PROVIDER_KEY);
        return OAuthProvider.valueOf(oAuthProvider);
    }

    public OAuthProviderInfo oAuthProviderInfo() {
        Variables providerInfoMap = new MapBackedVariables(variables.assertMap(PROVIDER_INFO_KEY));
        return new OAuthProviderInfo(new MapBackedVariables(providerInfoMap.assertMap(oAuthProvider().name())));
    }

    public String clientId() {
        return oAuthProviderInfo().clientId();
    }
    public OAuthProviderInfo.ClientSecret clientSecret() {
        return oAuthProviderInfo().clientSecret();
    }

    public enum OAuthProvider {
        GITHUB,
        AZURE
    }

    public static class OAuthProviderInfo {
        private final Variables variables;

        public OAuthProviderInfo(Variables variables) {
            this.variables = variables;
        }

        public String clientId() {
            return variables.assertString(CLIENT_ID_KEY);
        }
        public String authorityUrl() {
            return variables.assertString(AUTHORITY_URL_KEY);
        }
        public ClientSecret clientSecret() {
            return new ClientSecret(new MapBackedVariables(variables.assertMap(CLIENT_SECRET_KEY)));
        }

        public String scope() {
            return variables.assertString(SCOPE_KEY);
        }

        public static class ClientSecret {
            private final Variables variables;

            private static final String ORG_KEY = "org";
            private static final String SECRET_KEY = "secret";
            private static final String PASSWORD_KEY = "password";
            public ClientSecret(Variables variables) {
                this.variables = variables;
            }
            public String org() {
                return variables.assertString(ORG_KEY);
            }
            public String secret() {
                return variables.assertString(SECRET_KEY);
            }
            public String password() {
                return variables.getString(PASSWORD_KEY);
            }
        }
    }
}
