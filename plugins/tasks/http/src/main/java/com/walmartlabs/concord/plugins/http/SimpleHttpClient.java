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
import com.walmartlabs.concord.plugins.http.HttpTask.RequestType;
import com.walmartlabs.concord.plugins.http.exception.RequestTimeoutException;
import com.walmartlabs.concord.plugins.http.exception.UnauthorizedException;
import com.walmartlabs.concord.plugins.http.request.HttpTaskRequest;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.ws.rs.core.Response.Status.Family;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static com.walmartlabs.concord.plugins.http.HttpTask.ResponseType;
import static com.walmartlabs.concord.plugins.http.HttpTaskUtils.getHttpEntity;

public class SimpleHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SimpleHttpClient.class);

    private final Configuration config;
    private final CloseableHttpClient client;
    private final HttpUriRequest request;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final boolean dryRunMode;

    private SimpleHttpClient(Configuration config, boolean dryRunMode) throws Exception {
        this.config = config;
        this.client = createClient(config);
        this.request = buildHttpUriRequest(config);
        this.dryRunMode = dryRunMode;
    }

    /**
     * Factory method to create {@link SimpleHttpClient} objects
     *
     * @param config {@link Configuration}
     * @return SimpleHttpClient
     */
    public static SimpleHttpClient create(Configuration config, boolean dryRunMode) throws Exception {
        return new SimpleHttpClient(config, dryRunMode);
    }

    /**
     * Execute request in the {@link #config Configuration}, resulting in {@link ClientResponse}
     *
     * @return ClientResponse response result of the execution
     * @throws Exception exception
     */
    public ClientResponse execute() throws Exception {
        CloseableHttpResponse httpResponse = null;
        Object content = "";
        try {
            if (config.isDebug()) {
                logRequest(request);
            }

            if (dryRunMode && !HttpGet.METHOD_NAME.equals(request.getMethod())) {
                log.info("Running in dry-run mode: Skipping sending request");
                return new ClientResponse(Map.of("success", true, "statusCode", 200));
            }

            httpResponse = callWithTimeout(() -> this.client.execute(request), config.getRequestTimeout());

            int code = httpResponse.getStatusLine().getStatusCode();
            if (isUnauthorized(code) && !config.isIgnoreErrors()) {
                throw new UnauthorizedException("Authorization required for " + request.getURI().toURL() + "(code: " + code + ")");
            }

            Family statusCodeFamily = Family.familyOf(httpResponse.getStatusLine().getStatusCode());
            boolean isSuccess = Family.SUCCESSFUL == statusCodeFamily;

            Map<String, Object> response = new HashMap<>();
            if (isSuccess) {
                content = processResponse(httpResponse, config);
                response.put("content", content);
            } else {
                content = httpResponse.getEntity() != null ? EntityUtils.toString(httpResponse.getEntity()) : "";
                // for backward compatibility
                response.put("content", "");
                response.put("errorString", content);
            }

            response.put("success", isSuccess);
            response.put("statusCode", httpResponse.getStatusLine().getStatusCode());
            response.put("headers", getHeaders(httpResponse.getAllHeaders()));

            // TODO return a proper structure, convert later
            return new ClientResponse(response);
        } catch (RequestTimeoutException | IOException | UnauthorizedException e) {
            if (!config.isIgnoreErrors()) {
                throw e;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("errorString", e.getMessage());
            if (httpResponse != null) {
                response.put("statusCode", httpResponse.getStatusLine().getStatusCode());
            }

            // TODO return a proper structure, convert later
            return new ClientResponse(response);
        } finally {
            if (httpResponse != null) {
                if (config.isDebug()) {
                    logResponse(httpResponse, content);
                }

                httpResponse.close();
            }

            this.client.close();
        }
    }

    private <T> T callWithTimeout(Callable<T> callable, long timeoutDurationMs) throws Exception {
        Future<T> future = executorService.submit(callable);
        try {
            if (timeoutDurationMs > 0) {
                return future.get(timeoutDurationMs, TimeUnit.MILLISECONDS);
            } else {
                return future.get();
            }
        } catch (TimeoutException e) {
            future.cancel(true);

            if (!request.isAborted()) {
                request.abort();
            }

            throw new RequestTimeoutException("Request timeout after " + timeoutDurationMs + "ms");
        } catch (ExecutionException e) {
            return unwrapException(e);
        }
    }

    private <T> T unwrapException(ExecutionException e) throws Exception {
        if (e.getCause() instanceof IOException) {
            throw (IOException) e.getCause();
        } else {
            throw new Exception(e.getCause());
        }
    }

    private Object processResponse(HttpResponse r, Configuration cfg) throws IOException {
        ResponseType t = cfg.getResponseType();
        if (t == null) {
            t = ResponseType.STRING;
        }

        HttpEntity e = r.getEntity();
        if (e == null) {
            return null;
        }

        switch (t) {
            case FILE:
                return storeFile(e);
            case JSON:
                return parseJson(e);
            default:
                return EntityUtils.toString(e);
        }
    }

    private void logRequest(HttpUriRequest request) throws IOException {
        Map<String, Object> debugInfo = new HashMap<>();

        Map<String, Object> requestInfo = buildRequestInfo(request);
        debugInfo.put("requestInfo", requestInfo);

        log.info(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(debugInfo));
    }

    private void logResponse(CloseableHttpResponse httpResponse, Object content) throws IOException {
        Map<String, Object> debugInfo = new HashMap<>();

        Map<String, Object> responseInfo = buildResponseInfo(httpResponse, content);
        debugInfo.put("responseInfo", responseInfo);

        log.info(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(debugInfo));
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
     *
     * @param uri URI of the file
     * @param dir Directory in which to save the file
     * @return Path object representing the file
     * @throws IOException when the file cannot be created with {@link Files#createFile(Path, FileAttribute[])} or
     *                     {@link Files#createTempFile(Path, String, String, FileAttribute[])}
     */
    private Path uriToPath(java.net.URI uri, Path dir) throws IOException {
        String filename = null;
        String s = uri.toString();
        Path path;

        final int lastSlashIndex = s.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            filename = s.substring(lastSlashIndex + 1);
        }

        if (filename == null || filename.isEmpty()) {
            path = Files.createTempFile(dir, "tmpfile_", ".tmp");
        } else {
            path = Files.createFile(dir.resolve(filename));
        }

        return path;
    }

    /**
     * Method to parse the json response
     *
     * @param entity {@link HttpEntity}
     * @return parsed Json {@link Object}
     * @throws RuntimeException
     */
    private Object parseJson(HttpEntity entity) {
        try {
            return objectMapper.readValue(entity.getContent(), Object.class);
        } catch (IOException e) {
            throw new RuntimeException("Invalid JSON response: " + e.getMessage());
        }
    }

    /**
     * Method to check whether the status code is Authorized or not
     *
     * @param statusCode http status code
     * @return true if statusCode is UNAUTHORIZED (401)
     */
    private boolean isUnauthorized(int statusCode) {
        return HttpStatus.SC_UNAUTHORIZED == statusCode;
    }

    /**
     * Method to create {@link CloseableHttpClient client} with custom connection manager
     *
     * @return CloseableHttpClient client
     * @throws Exception exception
     */
    private static CloseableHttpClient createClient(Configuration cfg) throws Exception {
        RequestConfig.Builder c = RequestConfig.custom()
                .setConnectTimeout(cfg.getConnectTimeout())
                .setSocketTimeout(cfg.getSocketTimeout())
                .setRedirectsEnabled(cfg.isFollowRedirects());

        HttpClientBuilder clientBuilder = HttpClientBuilder.create();

        String proxy = cfg.getProxy();
        if (proxy != null) {
            log.info("Using proxy: {}", proxy);
            HttpHost proxyHost = HttpHost.create(proxy);
            c.setProxy(proxyHost);

            if (cfg.getProxyUser() != null) {
                log.info("Using proxy auth: {}:***", cfg.getProxyUser());

                CredentialsProvider proxyCredsProvider = new BasicCredentialsProvider();
                proxyCredsProvider.setCredentials(
                        new AuthScope(proxyHost.getHostName(), proxyHost.getPort()),
                        new UsernamePasswordCredentials(cfg.getProxyUser(), new String(cfg.getProxyPassword())));

                clientBuilder.setDefaultCredentialsProvider(proxyCredsProvider);
            }
        }

        return clientBuilder
                .setConnectionManager(buildConnectionManager(cfg))
                .setDefaultRequestConfig(c.build())
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
    }

    /**
     * Method to build the connection manager
     *
     * @return HttpClientConnectionManager
     * @throws KeyManagementException   keyManagementException
     * @throws NoSuchAlgorithmException noSuchAlgorithmException
     */
    private static HttpClientConnectionManager buildConnectionManager(Configuration cfg) throws Exception {
        SSLContextBuilder builder = new SSLContextBuilder();

        if (!cfg.isStrictSsl()) {
            builder.loadTrustMaterial(new TrustAllStrategy());
        }

        if (cfg.keyStorePath() != null) {
            Path keystorePath = Paths.get(cfg.getWorkDir()).resolve(cfg.keyStorePath());
            if (Files.notExists(keystorePath)) {
                throw new RuntimeException("Keystore '" + cfg.keyStorePath() + "' not found");
            }

            char[] keystorePass = cfg.keyStorePassword() != null ? cfg.keyStorePassword().toCharArray() : null;

            KeyStore keyStore = KeyStore.getInstance("pkcs12");
            try (InputStream keyStoreInput = Files.newInputStream(keystorePath)) {
                keyStore.load(keyStoreInput, keystorePass);
            }

            if (keyStore.size() == 0) {
                throw new RuntimeException("Keystore is empty. Remove keystore input parameters or fix keystore");
            }

            try {
                builder.loadKeyMaterial(keyStore, keystorePass);
            } catch (UnrecoverableKeyException e) {
                throw new RuntimeException("Get key failed. Check keystore password", e);
            }
        }

        if (cfg.trustStorePath() != null) {
            Path trustStorePath = Paths.get(cfg.getWorkDir()).resolve(cfg.trustStorePath());
            if (Files.notExists(trustStorePath)) {
                throw new RuntimeException("TrustStore '" + cfg.trustStorePath() + "' not found");
            }

            char[] trustStorePass = cfg.trustStorePassword() != null ? cfg.trustStorePassword().toCharArray() : null;

            try {
                builder.loadTrustMaterial(trustStorePath.toFile(), trustStorePass);
            } catch (Exception e) {
                throw new RuntimeException("load trustStore failed. Check truststore password", e);
            }
        }

        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(builder.build(), assertHostNameVerifier(cfg));

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", socketFactory)
                .build();

        return new PoolingHttpClientConnectionManager(registry);
    }


    private HttpUriRequest buildHttpUriRequest(Configuration cfg) throws Exception {
        switch (cfg.getMethodType()) {
            case DELETE:
                return buildDeleteRequest(cfg);
            case POST:
                return buildPostRequest(cfg);
            case GET:
                return buildGetRequest(cfg);
            case PUT:
                return buildPutRequest(cfg);
            case PATCH:
                return buildPatchRequest(cfg);
            default:
                throw new IllegalArgumentException("Unsupported method type: " + cfg.getMethodType());
        }
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
     * @throws Exception thrown by {@link HttpTaskUtils#getHttpEntity(Object, RequestType)} method
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
     * Method to build the patch request using the given configuration
     *
     * @param cfg {@link Configuration}
     * @return HttpUriRequest
     * @throws Exception thrown by {@link HttpTaskUtils#getHttpEntity(Object, RequestType)} method
     */
    private HttpUriRequest buildPatchRequest(Configuration cfg) throws Exception {
        return HttpTaskRequest.patch(cfg.getUrl())
                .withBasicAuth(cfg.getEncodedAuthToken())
                .withRequestType(cfg.getRequestType())
                .withResponseType(ResponseType.ANY)
                .withHeaders(cfg.getRequestHeaders())
                .withBody(getHttpEntity(cfg.getBody(), cfg.getRequestType()))
                .get();
    }

    /**
     * Method to build the put request using the given configuration
     *
     * @param cfg {@link Configuration}
     * @return HttpUriRequest
     * @throws Exception thrown by {@link HttpTaskUtils#getHttpEntity(Object, RequestType)} method
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

    private Map<String, Object> buildRequestInfo(HttpUriRequest request) throws IOException {
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("headers", getHeaders(request.getAllHeaders()));
        requestInfo.put("method", request.getMethod());
        requestInfo.put("url", request.getURI().toString());

        if ((config.getRequestType() != RequestType.FILE) && (request instanceof HttpEntityEnclosingRequest)) {
            try {
                String rsp = EntityUtils.toString(((HttpEntityEnclosingRequest) request).getEntity());
                requestInfo.put("body", rsp);
            } catch (ContentTooLongException e) {
                requestInfo.put("body", "...too long to dump...");
            }
        }

        return requestInfo;
    }

    private Map<String, Object> buildResponseInfo(CloseableHttpResponse httpResponse, Object content) {
        Map<String, Object> responseInfo = new HashMap<>();
        responseInfo.put("headers", getHeaders(httpResponse.getAllHeaders()));
        responseInfo.put("status", httpResponse.getStatusLine().getStatusCode());
        responseInfo.put("response", content);

        return responseInfo;
    }

    private static Map<String, String> getHeaders(Header[] headers) {
        if (headers == null) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        for (Header h : headers) {
            result.put(h.getName(), h.getValue());
        }
        return result;
    }

    /**
     * ClientResponse which wraps the response map. It is used to prevent the user to call the getResponse Method
     * without executing the request.
     */
    public class ClientResponse {
        private final Map<String, Object> response;

        private ClientResponse(Map<String, Object> response) {
            this.response = response;
        }

        public Map<String, Object> getResponse() {
            return response;
        }
    }

    private static HostnameVerifier assertHostNameVerifier(Configuration cfg) {
        if (cfg.isStrictSsl()) {
            return new DefaultHostnameVerifier(null);
        }

        return NoopHostnameVerifier.INSTANCE;
    }
}
