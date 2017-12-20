package com.walmartlabs.concord.server.rpc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.walmartlabs.concord.rpc.TSlackNotificationRequest;
import com.walmartlabs.concord.rpc.TSlackNotificationResponse;
import com.walmartlabs.concord.rpc.TSlackNotificationServiceGrpc;
import com.walmartlabs.concord.server.cfg.SlackConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import io.grpc.stub.StreamObserver;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Named
public class SlackServiceImpl extends TSlackNotificationServiceGrpc.TSlackNotificationServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SlackServiceImpl.class);
    private static final int RATE_LIMIT_TIMEOUT = 5;

    private final SlackSimpleClient slackClient;
    private final RateLimiter limiter;

    @Inject
    public SlackServiceImpl(SlackConfiguration cfg) throws Exception {
        this.slackClient = new SlackSimpleClient(cfg);
        this.limiter = RateLimiter.create(cfg.getRequestLimit());
    }

    @Override
    @WithTimer
    public void notify(TSlackNotificationRequest request, StreamObserver<TSlackNotificationResponse> responseObserver) {
        try {
            boolean canAcquire = limiter.tryAcquire(RATE_LIMIT_TIMEOUT, TimeUnit.SECONDS);
            if (!canAcquire) {
                log.error("notify ['{}'] -> rate limit exceeded", request.getInstanceId());
                responseObserver.onError(new TimeoutException("Rate limit exceeded"));
                return;
            }

            SlackSimpleClient.Response r = slackClient.notify(request.getSlackChannelId(), request.getNotificationText());
            responseObserver.onNext(convert(r));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("notify ['{}'] -> error", request.getInstanceId(), e);
            responseObserver.onError(e);
        }
    }

    private static TSlackNotificationResponse convert(SlackSimpleClient.Response r) {
        TSlackNotificationResponse.Builder result = TSlackNotificationResponse.newBuilder()
                .setOk(r.isOk());
        if (r.getError() != null) {
            result.setError(r.getError());
        }
        return result.build();
    }

    static class SlackSimpleClient {

        private static final Logger log = LoggerFactory.getLogger(SlackSimpleClient.class);
        private static final String SLACK_API_ROOT = "https://slack.com/api/";
        private static final String CHAT_POST_MESSAGE_CMD = "chat.postMessage";

        private final ObjectMapper objectMapper = new ObjectMapper();
        private final String authToken;
        private final CloseableHttpClient client;

        public SlackSimpleClient(SlackConfiguration cfg) throws Exception {
            this.authToken = cfg.getAuthToken();
            this.client = createClient(cfg);
        }

        public void close() throws IOException {
            client.close();
        }

        public Response notify(String channelId, String text) throws IOException {
            HttpPost request = new HttpPost(SLACK_API_ROOT + CHAT_POST_MESSAGE_CMD);

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("token", authToken));
            params.add(new BasicNameValuePair("as_user", "true"));
            params.add(new BasicNameValuePair("channel", channelId));
            params.add(new BasicNameValuePair("text", text));

            request.setEntity(new UrlEncodedFormEntity(params));

            try (CloseableHttpResponse response = client.execute(request)) {

                if (response.getEntity() == null) {
                    log.error("notify ['{}', '{}'] -> empty response", channelId, text);
                    return new Response(false, "internal-error");
                }

                Response r = objectMapper.readValue(response.getEntity().getContent(), Response.class);
                log.info("notify ['{}', '{}'] -> {}", channelId, text, r);
                return r;
            }
        }

        private CloseableHttpClient createClient(SlackConfiguration cfg) throws Exception {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());

            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
                    .build();

            log.warn("createClient -> accepting all SSL certificates");

            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(registry);
            cm.setDefaultMaxPerRoute(cfg.getMaxConnections());

            return HttpClientBuilder.create()
                    .setDefaultRequestConfig(createConfig(cfg))
                    .setConnectionManager(cm)
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
        private static class Response {

            private final boolean ok;
            private final String error;

            @JsonCreator
            public Response(
                    @JsonProperty("ok") boolean ok,
                    @JsonProperty("error") String error) {
                this.ok = ok;
                this.error = error;
            }

            public boolean isOk() {
                return ok;
            }

            public String getError() {
                return error;
            }

            @Override
            public String toString() {
                return "Response{" +
                        "ok=" + ok +
                        ", error='" + error + '\'' +
                        '}';
            }
        }

        private static class DefaultTrustManager implements X509TrustManager {

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }
    }
}
