package com.walmartlabs.concord.server.org.secret.store.keywhiz;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.walmartlabs.concord.server.cfg.KeywhizSecretStoreConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import javax.inject.Inject;
import javax.inject.Named;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.*;

@Named
public class KeywhizClient {

    private final KeywhizSecretStoreConfiguration cfg;
    private HttpClientConnectionManager cm;

    private final ObjectMapper objectMapper = createObjectMapper();

    @Inject
    public KeywhizClient(KeywhizSecretStoreConfiguration cfg) throws Exception {
        this.cfg = cfg;

        if (!cfg.isEnabled()) {
            return;
        }

        this.cm = createConnectionManager(cfg);
    }

    public void createSecret(String name, byte[] content) throws IOException {
        CreateOrUpdateSecretRequestV2 request = new CreateOrUpdateSecretRequestV2();
        request.setContent(Base64.getEncoder().encodeToString(content));
        request.setName(name);

        httpPost(cfg.getUrl() + "/automation/v2/secrets/" + name, request);
    }

    public void deleteSecret(String name) throws IOException {
        httpDelete(cfg.getUrl() + "/automation/v2/secrets/" + name);
    }

    public byte[] getSecret(String name) throws IOException {
        SecretContentsRequestV2 request = new SecretContentsRequestV2(Collections.singleton(name));

        String resp = httpPost(cfg.getUrl() + "/automation/v2/secrets/request/contents", request);
        SecretContentsResponseV2 r = objectMapper.readValue(resp, SecretContentsResponseV2.class);

        if (r.getSuccessSecrets() == null) {
            return null;
        }
        String result = r.getSuccessSecrets().get(name);
        if (result == null) {
            return null;
        }

        return Base64.getDecoder().decode(result);
    }

    private CloseableHttpClient createClient() {
        return HttpClients.custom()
                .setConnectionManager(cm)
                .setConnectionManagerShared(true)
                .build();
    }

    private static HttpClientConnectionManager createConnectionManager(KeywhizSecretStoreConfiguration cfg) throws Exception {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(createConnectionRegistry(cfg));
        cm.setDefaultSocketConfig(createSocketConfig(cfg));
        return cm;
    }

    private static Registry<ConnectionSocketFactory> createConnectionRegistry(KeywhizSecretStoreConfiguration cfg) throws Exception {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", createSslSocketFactory(cfg))
                .build();
    }

    private static SocketConfig createSocketConfig(KeywhizSecretStoreConfiguration cfg) {
        return SocketConfig.custom().setSoTimeout(cfg.getSoTimeout()).build();
    }

    private static ConnectionSocketFactory createSslSocketFactory(KeywhizSecretStoreConfiguration cfg) throws Exception {
        SSLContext sslcontext = SSLContexts.custom()
                .loadKeyMaterial(new File(cfg.getKeyStore()), cfg.getKeyStorePassword().toCharArray(), cfg.getKeyStorePassword().toCharArray(), null)
                .loadTrustMaterial(new File(cfg.getTrustStore()), cfg.getTrustStorePassword().toCharArray(), new TrustSelfSignedStrategy())
                .build();

        return new SSLConnectionSocketFactory(
                sslcontext,
                new String[]{"TLSv1.2"},
                null,
                (s, sslSession) -> true);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private String httpPost(String url, Object content) throws IOException {
        StringEntity entity = new StringEntity(objectMapper.writeValueAsString(content));

        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(entity);
        httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

        return makeCall(httpPost);
    }

    private void httpDelete(String url) throws IOException {
        HttpDelete httpDelete = new HttpDelete(url);
        httpDelete.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
        httpDelete.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

        makeCall(httpDelete);
    }

    private String makeCall(HttpUriRequest request) throws IOException {
        try (CloseableHttpClient client = createClient();
             CloseableHttpResponse response = client.execute(request)) {

            throwOnCommonError(response.getStatusLine());

            HttpEntity e = response.getEntity();
            if (e == null) {
                return null;
            }

            try (InputStream is = response.getEntity().getContent()) {
                return CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
            }
        }
    }

    private void throwOnCommonError(StatusLine statusLine) throws IOException {
        int response = statusLine.getStatusCode();
        if (Response.Status.Family.SUCCESSFUL != Response.Status.Family.familyOf(response)) {
            throw new IOException("Query error. Server response: " + response + " (" + statusLine.getReasonPhrase() + ")");
        }
    }

    private static class SecretContentsRequestV2 {

        @JsonProperty("secrets")
        public Set<String> secrets;

        public SecretContentsRequestV2() {
        }

        public SecretContentsRequestV2(Set<String> secrets) {
            this.secrets = secrets;
        }

        public Set<String> getSecrets() {
            return secrets;
        }

        public void setSecrets(Set<String> secrets) {
            this.secrets = secrets;
        }
    }

    private static class SecretContentsResponseV2 {

        @JsonProperty("successSecrets")
        public Map<String, String> successSecrets;

        @JsonProperty("missingSecrets")
        public List<String> missingSecrets;

        public Map<String, String> getSuccessSecrets() {
            return successSecrets;
        }

        public void setSuccessSecrets(Map<String, String> successSecrets) {
            this.successSecrets = successSecrets;
        }

        public List<String> getMissingSecrets() {
            return missingSecrets;
        }

        public void setMissingSecrets(List<String> missingSecrets) {
            this.missingSecrets = missingSecrets;
        }

        @Override
        public String toString() {
            return "SecretContentsResponseV2{" +
                    "successSecrets=" + successSecrets +
                    ", missingSecrets=" + missingSecrets +
                    '}';
        }
    }

    private class CreateOrUpdateSecretRequestV2 implements Serializable {

        @JsonProperty("name")
        public String name;

        @JsonProperty("content")
        public String content;

        @JsonProperty("description")
        public String description;

        @JsonProperty("metadata")
        public ImmutableMap<String, String> metadata;

        @JsonProperty("expiry")
        public long expiry;

        @JsonProperty("type")
        public String type;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public ImmutableMap<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(ImmutableMap<String, String> metadata) {
            this.metadata = metadata;
        }

        public long getExpiry() {
            return expiry;
        }

        public void setExpiry(long expiry) {
            this.expiry = expiry;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
