package com.walmartlabs.concord.server.plugins.iamsso;

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
import javax.inject.Named;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Named
public class SsoClient {

    private static final Logger log = LoggerFactory.getLogger(SsoClient.class);

    private static final String USER_AGENT_HEADER = "Mozilla/5.0";
    private static final String ACCEPT_LANG_HEADER = "en-US,en;q=0.5";
    private static final String CONTENT_TYPE_HEADER = "application/json; charset=UTF-8";

    private static final String TOKEN_REQUEST = "{" +
            "\"grant_type\":\"authorization_code\"," +
            "\"code\":\"%s\"," +
            "\"redirect_uri\":\"%s\"" + "}";

    private final SsoConfiguration cfg;

    private final ObjectMapper objectMapper;

    @Inject
    public SsoClient(SsoConfiguration cfg, ObjectMapper objectMapper) {
        this.cfg = cfg;
        this.objectMapper = objectMapper;

        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Token getToken(String authCode, String clientRedirectURI) throws IOException {
        // TODO: replace with okHttp?
        URL url = new URL(cfg.getTokenEndPointUrl());
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
            String clientIdAndSecret = String.format("%s:%s", cfg.getClientId(), cfg.getClientSecret());
            String authzHeaderValue = String.format("Basic %s", Base64.getEncoder().encodeToString(clientIdAndSecret.getBytes()));

            con.setRequestProperty(HttpHeaders.AUTHORIZATION, authzHeaderValue);
            con.setRequestProperty(HttpHeaders.USER_AGENT, USER_AGENT_HEADER);
            con.setRequestProperty(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_HEADER);
            con.setRequestProperty(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANG_HEADER);

            con.setRequestMethod("POST");
            con.setConnectTimeout(cfg.getTokenServiceConnectTimeout());
            con.setReadTimeout(cfg.getTokenServiceReadTimeout());
            con.setDoOutput(true);

            String request = String.format(TOKEN_REQUEST, authCode, clientRedirectURI);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream(), StandardCharsets.UTF_8));
            out.write(request);
            out.close();

            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                log.error("getToken ['{}'] -> error response code {}", authCode, responseCode);
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
        String idToken();
    }
}
