package com.walmartlabs.concord.plugins.http;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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


import com.walmartlabs.concord.plugins.http.exception.UnauthorizedException;
import com.walmartlabs.concord.plugins.http.request.HttpTaskRequest;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import static com.walmartlabs.concord.plugins.http.HttpTask.ResponseType;
import static com.walmartlabs.concord.plugins.http.HttpTaskUtils.getHttpEntity;

public class SimpleHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SimpleHttpClient.class);
    private Configuration config;
    private CloseableHttpClient client;
    private HttpUriRequest request;

    private SimpleHttpClient(Configuration config) throws Exception {
        this.config = config;
        this.client = createClient();
        this.request = buildHttpUriRequest(config);
    }


    /**
     * Factory method to create {@link SimpleHttpClient} objects
     *
     * @param config {@link Configuration}
     * @return SimpleHttpClient
     */
    public static SimpleHttpClient create(Configuration config) throws Exception {
        return new SimpleHttpClient(config);
    }

    /**
     * Execute request in the {@link #config Configuration}, resulting in {@link ClientResponse}
     *
     * @return ClientResponse response result of the execution
     * @throws Exception exception
     */
    public ClientResponse execute() throws Exception {
        try (CloseableHttpResponse httpResponse = this.client.execute(request)) {

            if (isUnAuthorized(httpResponse.getStatusLine().getStatusCode())) {
                throw new UnauthorizedException("Authorization required for " + request.getURI().toURL());
            }

            log.info("Response status code: {}", httpResponse.getStatusLine().getStatusCode());

            HttpTaskResponse response = new HttpTaskResponse();

            if (Response.Status.Family.SUCCESSFUL == Response.Status.Family.familyOf(httpResponse.getStatusLine().getStatusCode())) {
                String responseString;
                if (config.getResponseType() == ResponseType.FILE) {
                    responseString = storeFile(httpResponse.getEntity());
                } else {
                    responseString = EntityUtils.toString(httpResponse.getEntity());
                }

                response.setSuccess(true);
                response.setContent(responseString);
            } else {
                response.setErrorString(EntityUtils.toString(httpResponse.getEntity()));

            }
            response.setStatusCode(httpResponse.getStatusLine().getStatusCode());

            return new ClientResponse(response);
        }
    }

    /**
     * Method to store the file into the .tmp folder under working directory
     *
     * @param entity {@link HttpEntity}
     * @return File relative path in {@link String}
     * @throws IOException exception from {@link Files#createTempFile(Path, String, String, FileAttribute[])}
     */
    private String storeFile(HttpEntity entity) throws IOException {
        Path baseDir = Paths.get(config.getWorkDir());
        Path tmpDir = assertTempDir(baseDir);

        Path tempFile = Files.createTempFile(tmpDir, "temp_", ".bin");
        entity.writeTo(new FileOutputStream(tempFile.toFile()));
        // Return the relative path instead of absolute path
        return baseDir.relativize(tempFile.toAbsolutePath()).toString();
    }

    /**
     * Method to make sure the .tmp directory exists in the working directory
     *
     * @param baseDir working directory
     * @return Path of the .tmp directory
     * @throws IOException from {@link Files#createDirectories(Path, FileAttribute[])}
     */
    private Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(".tmp");
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }
        return p;
    }

    /**
     * Method to check whether the status code is Authorized or not
     *
     * @param statusCode http status code
     * @return true if statusCode is UNAUTHORIZED (401)
     */
    private boolean isUnAuthorized(int statusCode) {
        return HttpStatus.SC_UNAUTHORIZED == statusCode;
    }

    /**
     * Method to create {@link CloseableHttpClient client} with custom connection manager
     *
     * @return CloseableHttpClient client
     * @throws Exception exception
     */
    private static CloseableHttpClient createClient() throws Exception {
        HttpClientBuilder builder = HttpClientBuilder.create()
                .setConnectionManager(buildConnectionManager());
        return builder.build();
    }

    /**
     * Method to build the connection manager
     *
     * @return HttpClientConnectionManager
     * @throws KeyManagementException   keyManagementException
     * @throws NoSuchAlgorithmException noSuchAlgorithmException
     */
    private static HttpClientConnectionManager buildConnectionManager() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                builder.build(), NoopHostnameVerifier.INSTANCE);

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", socketFactory)
                .build();

        return new PoolingHttpClientConnectionManager(registry);
    }

    private HttpUriRequest buildHttpUriRequest(Configuration config) throws Exception {
        HttpUriRequest request;
        switch (config.getMethodType()) {
            case POST:
                request = buildPostRequest(config);
                break;
            case GET:
                request = buildGetRequest(config);
                break;
            default:
                throw new IllegalArgumentException("Unsupported method type: " + config.getMethodType());
        }
        return request;
    }

    /**
     * Method to build the post request using the given configuration
     *
     * @param cfg {@link Configuration}
     * @return HttpUriRequest
     * @throws Exception thrown by {@link HttpTaskUtils#getHttpEntity(Object, HttpTask.RequestType)} method
     */
    private HttpUriRequest buildPostRequest(Configuration cfg) throws Exception {
        return HttpTaskRequest.Post(cfg.getUrl())
                .withBasicAuth(cfg.getEncodedAuthToken())
                .withRequestType(cfg.getRequestType())
                .withResponseType(cfg.getResponseType())
                .withBody(getHttpEntity(cfg.getBody(), cfg.getRequestType()))
                .get();
    }

    /**
     * Method to build the get request using the given configuration
     *
     * @param cfg {@link Configuration}
     * @return HttpUriRequest
     */
    private HttpUriRequest buildGetRequest(Configuration cfg) {
        return HttpTaskRequest.Get(cfg.getUrl())
                .withBasicAuth(cfg.getEncodedAuthToken())
                .withRequestType(cfg.getRequestType())
                .withResponseType(cfg.getResponseType())
                .get();
    }

    /**
     * ClientResponse which wraps the {@link HttpTaskResponse}. It is used to prevent the user to call the getResponse Method
     * without executing the request.
     */
    public class ClientResponse {
        private HttpTaskResponse response;

        private ClientResponse(HttpTaskResponse response) {
            this.response = response;
        }

        public HttpTaskResponse getResponse() {
            return response;
        }
    }

}
