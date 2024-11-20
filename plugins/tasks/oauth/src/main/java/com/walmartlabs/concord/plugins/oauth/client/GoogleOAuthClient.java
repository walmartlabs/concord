package com.walmartlabs.concord.plugins.oauth.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import org.apache.http.client.utils.URIBuilder;

import java.net.URISyntaxException;
import java.util.Map;

public class GoogleOAuthClient extends OAuthClient {

    private static final String authorizationUrl = "/login/oauth/authorize";
    private static final String tokenUrl = "/login/oauth/access_token";

    public GoogleOAuthClient(String authorityUrl, String concordBaseUrl) throws URISyntaxException {
        super(concordBaseUrl,
                new URIBuilder(authorityUrl + authorizationUrl).toString(),
                new URIBuilder(authorityUrl + tokenUrl).toString());
    }


    @Override
    protected Map<String, String> getAuhorizationUrlParams(String clientId, String state, String scope) {
        return Map.of("client_id", clientId, "redirect_uri", redirectUrl, "scope", scope, "state", state, "response_type", "code");
    }

    @Override
    protected String getAccessTokenPostData(String clientId, String clientSecret, String authorizationCode) {
        return "client_id=" + clientId + "&client_secret=" + clientSecret + "&code=" + authorizationCode + "&redirect_uri=" + redirectUrl;
    }
}
