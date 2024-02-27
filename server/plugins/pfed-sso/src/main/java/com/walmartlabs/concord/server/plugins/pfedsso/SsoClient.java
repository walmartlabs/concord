package com.walmartlabs.concord.server.plugins.pfedsso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.http.HttpHeaders;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

public class SsoClient {

    private static final Logger log = LoggerFactory.getLogger(SsoClient.class);

    private static final String USER_AGENT_HEADER = "Mozilla/5.0";
    private static final String ACCEPT_LANG_HEADER = "en-US,en;q=0.5";
    private static final String CONTENT_TYPE_HEADER = "application/x-www-form-urlencoded";
    private static final String CHARSET_HEADER = "utf-8";
    private static final String TOKEN_REQUEST = "code=%s&redirect_uri=%s&grant_type=authorization_code&client_id=%s";
    private static final String REVOKE_TOKEN_REQUEST = "token=%s&token_type_hint=refresh_token&client_id=%s";
    private static final String TOKEN_BY_REFRESHER_REQUEST = "refresh_token=%s&grant_type=refresh_token&client_id=%s";

    private final SsoConfiguration cfg;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public SsoClient(SsoConfiguration cfg) {
        this.cfg = cfg;
    }

    public Token getToken(String authCode, String clientRedirectURI) throws IOException {
        String urlParameters = String.format(TOKEN_REQUEST, authCode, clientRedirectURI, cfg.getClientId());
        return getToken(urlParameters);
    }

    public Token getTokenByRefreshToken(String refreshToken) throws IOException {
        String urlParameters = String.format(TOKEN_BY_REFRESHER_REQUEST, refreshToken, cfg.getClientId());
        return getToken(urlParameters);
    }

    public void revokeToken(String refreshToken) throws IOException {
        HttpURLConnection con = null;
        try {
            URL url = new URL(cfg.getLogoutEndpointUrl());
            con = (HttpURLConnection) url.openConnection();
            String urlParameters = String.format(REVOKE_TOKEN_REQUEST, refreshToken, cfg.getClientId());
            postRequest(con, urlParameters);
            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                log.error("refreshToken ['{}'] -> error response code {}", refreshToken, responseCode);
                throw new IOException("Invalid server response code: " + responseCode);
            }

        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    public String getTokenSigningKey() throws IOException {
        if (cfg.getTokenSigningKeyUrl() == null) {
            return null;
        }
        HttpURLConnection con = null;
        try {
            URL url = new URL(cfg.getTokenSigningKeyUrl());
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout((int) cfg.getTokenServiceConnectTimeout().toMillis());
            con.setReadTimeout((int) cfg.getTokenServiceReadTimeout().toMillis());
            con.setDoOutput(true);
            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                log.error("getTokenSigningKey ['{}'] -> error response code {}", cfg.getTokenSigningKeyUrl(), responseCode);
                throw new IOException("Invalid server response code: " + responseCode);
            }
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            return content.toString();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    public Profile getUserProfileByRefreshToken(String refreshToken) throws IOException {
        Token token = getTokenByRefreshToken(refreshToken);
        return getProfile(token.accessToken());
    }

    private Token getToken(String urlParameters) throws IOException {
        HttpURLConnection con = null;
        try {
            URL url = new URL(cfg.getTokenEndPointUrl());
            con = (HttpURLConnection) url.openConnection();
            postRequest(con, urlParameters);
            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                log.error("getToken ['{}'] -> error response code {}", urlParameters, responseCode);
                throw new IOException("Invalid server response code: " + responseCode);
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                return objectMapper.readValue(in, Token.class);
            }
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private void postRequest(HttpURLConnection con, String urlParameters) throws IOException {
        String clientIdAndSecret = String.format("%s:%s", cfg.getClientId(), cfg.getClientSecret());
        String authzHeaderValue = String.format("Basic %s", Base64.getEncoder().encodeToString(clientIdAndSecret.getBytes()));

        con.setRequestProperty(HttpHeaders.AUTHORIZATION, authzHeaderValue);
        con.setRequestProperty(HttpHeaders.USER_AGENT, USER_AGENT_HEADER);
        con.setRequestProperty(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_HEADER);
        con.setRequestProperty(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANG_HEADER);
        con.setRequestProperty(HttpHeaders.ACCEPT_CHARSET, CHARSET_HEADER);

        con.setRequestMethod("POST");
        con.setConnectTimeout((int) cfg.getTokenServiceConnectTimeout().toMillis());
        con.setReadTimeout((int) cfg.getTokenServiceReadTimeout().toMillis());
        con.setDoOutput(true);

        byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
        int postDataLength = postData.length;
        con.setRequestProperty(HttpHeaders.CONTENT_LENGTH, Integer.toString(postDataLength));

        try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
            wr.write(postData);
            wr.flush();
        }
    }

    public Profile getProfile(String accessToken) throws IOException {
        if (cfg.getUserInfoEndpointUrl() == null) {
            return null;
        }
        HttpURLConnection con = null;
        try {
            URL url = new URL(cfg.getUserInfoEndpointUrl());
            con = (HttpURLConnection) url.openConnection();
            String authzHeaderValue = String.format("Bearer %s", accessToken);
            con.setRequestProperty(HttpHeaders.AUTHORIZATION, authzHeaderValue);
            con.setRequestProperty(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_HEADER);
            con.setRequestMethod("GET");
            con.setConnectTimeout((int) cfg.getTokenServiceConnectTimeout().toMillis());
            con.setReadTimeout((int) cfg.getTokenServiceReadTimeout().toMillis());
            con.setDoOutput(true);
            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                log.error("getProfile ['{}'] -> error response code {}", cfg.getUserInfoEndpointUrl(), responseCode);
                throw new IOException("Invalid server response code: " + responseCode);
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                return objectMapper.readValue(in, Profile.class);
            }
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableToken.class)
    @JsonDeserialize(as = ImmutableToken.class)
    public interface Token {

        @JsonProperty("access_token")
        @Nullable
        String accessToken();

        @JsonProperty("token_type")
        String tokenType();

        @JsonProperty("expires_in")
        @Nullable
        Integer expiresIn();

        @JsonProperty("id_token")
        @Nullable
        String idToken();

        @JsonProperty("refresh_token")
        String refreshToken();
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableProfile.class)
    @JsonDeserialize(as = ImmutableProfile.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public interface Profile {

        @JsonProperty("sub")
        String sub();

        @JsonProperty("sAMAccountName")
        String userId();

        @JsonProperty("mail")
        String mail();

        @JsonProperty("displayName")
        @Nullable
        String displayName();

        @JsonProperty("userPrincipalName")
        @Nullable
        String userPrincipalName();

        @JsonProperty("distinguishedName")
        @Nullable
        String nameInNamespace();

        @JsonProperty("memberOf")
        @Nullable
        Set<String> groups();
    }
}
