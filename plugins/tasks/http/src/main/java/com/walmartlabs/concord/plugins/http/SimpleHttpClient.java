package com.walmartlabs.concord.plugins.http;

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



import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.plugins.http.exception.UnauthorizedException;
import com.walmartlabs.concord.plugins.http.request.HttpTaskRequest;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
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
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.plugins.http.HttpTask.ResponseType;
import static com.walmartlabs.concord.plugins.http.HttpTaskUtils.getHttpEntity;

public class SimpleHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SimpleHttpClient.class);

    private final Configuration config;
    private final CloseableHttpClient client;
    private final HttpUriRequest request;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            Map<String, Object> response = new HashMap<>();

            Object content = "";
            boolean isSuccess = false;
            if (Response.Status.Family.SUCCESSFUL == Response.Status.Family.familyOf(httpResponse.getStatusLine().getStatusCode())) {
                content = processResponse(httpResponse, config);
                isSuccess = true;
            } else {
                response.put("errorString", EntityUtils.toString(httpResponse.getEntity()));
            }

            response.put("success", isSuccess);
            response.put("content", content);
            response.put("statusCode", httpResponse.getStatusLine().getStatusCode());

            return new ClientResponse(response);
        }
    }

    private Object processResponse(HttpResponse r, Configuration cfg) throws IOException {
        ResponseType t = cfg.getResponseType();
        if (t == null) {
            t = ResponseType.STRING;
        }

        HttpEntity e = r.getEntity();

        switch (t) {
            case FILE: {
                return storeFile(e);
            }
            case JSON: {
                return objectMapper.readValue(e.getContent(), Object.class);
            }
            default: {
                return EntityUtils.toString(e);
            }
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
        Path tempFile = uriToPath(this.request.getURI(), tmpDir);

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
        return p.resolve(Files.createTempDirectory(p, "tmpdir_"));
    }

    /**
     * Converts a URI to a temporary Path. If no filename can be parsed from the URI, then a filename will be generated
     * @param uri URI of the file
     * @param dir Directory in which to save the file
     * @return Path object representing the file
     * @throws IOException when the file cannot be created with {@link Files#createFile(Path, FileAttribute[])} or
     * {@link Files#createTempFile(Path, String, String, FileAttribute[])}
     */
    private Path uriToPath(java.net.URI uri, Path dir) throws IOException {
        String filename = null;
        String s = uri.toString();
        Path path;

        final int lastSlashIndex = s.lastIndexOf("/");
        if (lastSlashIndex > 0) {
            filename = s.substring(lastSlashIndex+1);
        }

        if (filename == null || filename.isEmpty()) {
            path = Files.createTempFile(dir, "tmpfile_", ".tmp");
        } else {
            path = Files.createFile(dir.resolve(filename));
        }

        return  path;
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

    private HttpUriRequest buildHttpUriRequest(Configuration cfg) throws Exception {
        HttpUriRequest request;
        switch (cfg.getMethodType()) {
            case DELETE: {
                request = buildDeleteRequest(cfg);
                break;
            }
            case POST: {
                request = buildPostRequest(cfg);
                break;
            }
            case GET: {
                request = buildGetRequest(cfg);
                break;
            }
            case PUT: {
                request = buildPutRequest(cfg);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported method type: " + cfg.getMethodType());
        }
        return request;
    }

    /**
     * Method to build the delete request using the given configuration
     *
     * @param cfg {@link Configuration}
     * @return HttpUriRequest
     */
    private HttpUriRequest buildDeleteRequest(Configuration cfg) {
        return HttpTaskRequest.delete(cfg.getUrl())
                .withBasicAuth(cfg.getEncodedAuthToken())
                .withResponseType(ResponseType.ANY)
                .withHeaders(cfg.getRequestHeaders())
                .get();
    }

    /**
     * Method to build the post request using the given configuration
     *
     * @param cfg {@link Configuration}
     * @return HttpUriRequest
     * @throws Exception thrown by {@link HttpTaskUtils#getHttpEntity(Object, HttpTask.RequestType)} method
     */
    private HttpUriRequest buildPostRequest(Configuration cfg) throws Exception {
        return HttpTaskRequest.post(cfg.getUrl())
                .withBasicAuth(cfg.getEncodedAuthToken())
                .withRequestType(cfg.getRequestType())
                .withResponseType(ResponseType.ANY)
                .withHeaders(cfg.getRequestHeaders())
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
        return HttpTaskRequest.get(cfg.getUrl())
                .withBasicAuth(cfg.getEncodedAuthToken())
                .withRequestType(cfg.getRequestType())
                .withResponseType(ResponseType.ANY)
                .withHeaders(cfg.getRequestHeaders())
                .get();
    }

    /**
     * Method to build the put request using the given configuration
     *
     * @param cfg {@link Configuration}
     * @return HttpUriRequest
     * @throws Exception thrown by {@link HttpTaskUtils#getHttpEntity(Object, HttpTask.RequestType)} method
     */
    private HttpUriRequest buildPutRequest(Configuration cfg) throws Exception {
        return HttpTaskRequest.put(cfg.getUrl())
                .withBasicAuth(cfg.getEncodedAuthToken())
                .withRequestType(cfg.getRequestType())
                .withResponseType(ResponseType.ANY)
                .withHeaders(cfg.getRequestHeaders())
                .withBody(getHttpEntity(cfg.getBody(), cfg.getRequestType()))
                .get();
    }

    /**
     * ClientResponse which wraps the response map. It is used to prevent the user to call the getResponse Method
     * without executing the request.
     */
    public class ClientResponse {
        private Map<String, Object> response;

        private ClientResponse(Map<String, Object> response) {
            this.response = response;
        }

        public Map<String, Object> getResponse() {
            return response;
        }
    }
}
