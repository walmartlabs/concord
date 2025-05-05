package com.walmartlabs.concord.plugins.slack;

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

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.TimeProvider;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SlackClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SlackClient.class);

    private static final String SLACK_API_ROOT = "https://slack.com/api/";
    private static final String CHAT_POST_MESSAGE_CMD = "chat.postMessage";
    private static final String CHAT_UPDATE_MESSAGE_CMD = "chat.update";
    private static final String MESSAGE_ADD_REACTION_CMD = "reactions.add";
    private static final String CREATE_CHANNEL_CMD = "channels.create";
    private static final String CREATE_GROUP_CMD = "groups.create";
    private static final String ARCHIVE_CHANNEL_CMD = "channels.archive";
    private static final String ARCHIVE_CONVERSATION_CMD = "conversations.archive";
    private static final String ARCHIVE_GROUP_CMD = "groups.archive";
    private static final String AS_USER = "as_user";
    private static final String CHANNEL = "channel";

    private static final int TOO_MANY_REQUESTS_ERROR = 429;
    private static final int DEFAULT_RETRY_AFTER = 10;

    private final SlackConfiguration slackCfg;
    private final int retryCount;
    private final TimeProvider timeProvider;
    private final PoolingHttpClientConnectionManager connManager;
    private final CloseableHttpClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlackClient(SlackConfiguration cfg, TimeProvider timeProvider) {
        this.retryCount = cfg.getRetryCount();
        this.timeProvider = timeProvider;
        this.connManager = createConnManager();
        this.client = createClient(cfg, connManager);
        this.slackCfg = cfg;
    }

    @Override
    public void close() throws IOException {
        client.close();
        connManager.close();
    }

    public Response addReaction(String channelId, String ts, String reaction) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put(CHANNEL, channelId);
        params.put("timestamp", ts);
        params.put("name", reaction);

        return exec(MESSAGE_ADD_REACTION_CMD, params);
    }

    public Response postJsonMessage(String json) throws IOException {
        return exec(CHAT_POST_MESSAGE_CMD, json);
    }

    public Response updateJsonMessage(String json) throws IOException {
        return exec(CHAT_UPDATE_MESSAGE_CMD, json);
    }

    public Response message(String channelId, String ts, boolean replyBroadcast, String text, String iconEmoji, String username, Collection<Object> attachments) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put(CHANNEL, channelId);

        if (slackCfg.isLegacy()) {
            params.put(AS_USER, true);
        }

        params.put("text", text);

        if (ts != null) {
            params.put("thread_ts", ts);
            params.put("reply_broadcast", replyBroadcast);
        }

        if (iconEmoji != null) {
            params.put("icon_emoji", iconEmoji);
            if (slackCfg.isLegacy()) {
                params.put(AS_USER, false);
            }
        }

        if (username != null) {
            params.put("username", username);
            if (slackCfg.isLegacy()) {
                params.put(AS_USER, false);
            }
        }

        if (attachments != null) {
            params.put("attachments", attachments);
        }

        return exec(CHAT_POST_MESSAGE_CMD, params);
    }

    public Response createChannel(String channelName) throws IOException {
        return exec(CREATE_CHANNEL_CMD, Collections.singletonMap("name", channelName));
    }

    public Response createGroupChannel(String channelName) throws IOException {
        return exec(CREATE_GROUP_CMD, Collections.singletonMap("name", channelName));
    }

    public Response archiveChannel(String channelId) throws IOException {
        return exec(ARCHIVE_CHANNEL_CMD, Collections.singletonMap(CHANNEL, channelId));
    }

    public Response archiveConversation(String id) throws IOException {
        return exec(ARCHIVE_CONVERSATION_CMD, Map.of(CHANNEL, id));
    }

    public Response archiveGroup(String channelId) throws IOException {
        return exec(ARCHIVE_GROUP_CMD, Collections.singletonMap(CHANNEL, channelId));
    }

    private Response exec(String command, Map<String, Object> params) throws IOException {
        return exec(command, objectMapper.writeValueAsString(params));
    }

    private Response exec(String command, String json) throws IOException {
        int retryAfter = DEFAULT_RETRY_AFTER;
        IOException lastException = null;
        Integer statusCode = null;

        HttpPost request = new HttpPost(SLACK_API_ROOT + command);
        request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

        for (int attemptNo = 0; attemptNo <= retryCount; attemptNo++) {
            if (attemptNo > 0) {
                sleep(retryAfter * 1000L);
                log.info("exec [{}, {}] -> attempt #{}. Retrying request after {} sec ...",
                        command, json, attemptNo, retryAfter);
            }

            try (CloseableHttpResponse response = client.execute(request)) {
                statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == HttpStatus.SC_OK) {
                    return parseResponse(response, command, json);
                }

                if (statusCode == TOO_MANY_REQUESTS_ERROR) {
                    retryAfter = getRetryAfter(response);
                }

                log.warn("exec ['{}', '{}'] -> response code: {}", command, json, statusCode);
            } catch (IOException ie) {
                log.error("exec [{}, {}] -> {}", command, json, ie.getMessage());
                lastException = ie;
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        String error = "Slack request not successful after " + retryCount + " retries. Last response code: " + statusCode;
        return new Response(false, null, error);
    }

    private Response parseResponse(CloseableHttpResponse response, String command,
                                   String json) throws IOException {
        Response r;

        if (response.getEntity() == null) {
            log.error("exec ['{}', '{}'] -> empty response", command, json);
            r = new Response(false, null, "empty response");
        } else {
            String s = EntityUtils.toString(response.getEntity());
            r = objectMapper.readValue(s, Response.class);
            log.debug("exec ['{}', '{}'] -> {}", command, json, r);
        }

        return r;
    }

    private static int getRetryAfter(HttpResponse response) {
        Header h = response.getFirstHeader("Retry-After");
        if (h == null) {
            return DEFAULT_RETRY_AFTER;
        }

        try {
            return Integer.parseInt(h.getValue());
        } catch (Exception e) {
            log.warn("getRetryAfter -> can't parse retry value '{}'", h.getValue());
            return DEFAULT_RETRY_AFTER;
        }
    }

    private void sleep(long t) {
        try {
            timeProvider.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PoolingHttpClientConnectionManager createConnManager() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());

            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
                    .build();

            return new PoolingHttpClientConnectionManager(registry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CloseableHttpClient createClient(SlackConfiguration cfg, HttpClientConnectionManager connManager) {
        Collection<Header> headers = Collections.singleton(new BasicHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getAuthToken()));
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(createConfig(cfg))
                .setConnectionManager(connManager)
                .setDefaultHeaders(headers)
                .build();
    }

    private static RequestConfig createConfig(SlackConfiguration cfg) {
        HttpHost proxy = null;
        if (cfg.getProxyAddress() != null) {
            proxy = new HttpHost(cfg.getProxyAddress(), cfg.getProxyPort(), "http");
        }

        return RequestConfig.custom()
                .setConnectTimeout(cfg.getConnectTimeout())
                .setSocketTimeout(cfg.getSoTimeout())
                .setProxy(proxy)
                .build();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {

        private final boolean ok;
        private final String ts;
        private final String error;
        private final Map<String, Object> params = new HashMap<>();

        @JsonCreator
        public Response(@JsonProperty("ok") boolean ok,
                        @JsonProperty("ts") String ts,
                        @JsonProperty("error") String error) {

            this.ok = ok;
            this.ts = ts;
            this.error = error;
        }

        public boolean isOk() {
            return ok;
        }

        public String getError() {
            return error;
        }

        public String getTs() {
            return ts;
        }

        @JsonAnyGetter
        public Map<String, Object> getParams() {
            return params;
        }

        @JsonAnySetter
        public void setParams(String name, Object value) {
            params.put(name, value);
        }

        @Override
        public String toString() {
            return "Response{" +
                    "ok=" + ok +
                    ", ts=" + ts +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

    private static class DefaultTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { // NOSONAR
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { // NOSONAR
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
