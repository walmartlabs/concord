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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.plugins.oauth.client.model.TokenResponse;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public abstract class OAuthClient {

    private final String authorizationUrl;
    private final String tokenUrl;
    protected final String redirectUrl;

    public OAuthClient(String concordBaseUrl, String authorizationUrl, String tokenUrl) throws URISyntaxException {
        this.authorizationUrl = authorizationUrl;
        this.tokenUrl = tokenUrl;
        this.redirectUrl = new URIBuilder(concordBaseUrl + "/api/v1/oauth/callback").toString();
    }

    protected abstract Map<String,String> getAuhorizationUrlParams(String clientId, String state, String scope);

    public String buildAuthorizationUrl(String clientId, String state, String scope) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(authorizationUrl);
        Map<String, String> uriParams = getAuhorizationUrlParams(clientId, state, scope);
        uriParams.forEach(uriBuilder::addParameter);
        return uriBuilder.build().toString();
    }

    protected abstract String getAccessTokenPostData(String clientId, String clientSecret, String authorizationCode);

    public TokenResponse getAccessToken(String clientId, String clientSecret, String authorizationCode) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String postData = getAccessTokenPostData(clientId,
                clientSecret,
                authorizationCode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(postData))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readValue(response.body(), TokenResponse.class);
    }
}
