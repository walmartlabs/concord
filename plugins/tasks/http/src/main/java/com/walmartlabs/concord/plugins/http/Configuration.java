package com.walmartlabs.concord.plugins.http;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.plugins.http.HttpTask.RequestType;
import com.walmartlabs.concord.plugins.http.HttpTask.ResponseType;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.MapUtils;
import org.apache.http.client.utils.URIBuilder;

import java.util.*;

import static com.walmartlabs.concord.plugins.http.HttpTask.HttpTaskConstant.*;
import static com.walmartlabs.concord.plugins.http.HttpTask.RequestMethodType;
import static javax.xml.transform.OutputKeys.METHOD;

/**
 * Configuration for {@link SimpleHttpClient}
 *
 * @see SimpleHttpClient
 */
public class Configuration {

    private final String url;
    private final String encodedAuthToken;
    private final RequestType requestType;
    private final ResponseType responseType;
    private final String workDir;
    private final RequestMethodType methodType;
    private final Map<String, String> requestHeaders;
    private final Object body;
    private final int connectTimeout;
    private final int socketTimeout;
    private final int requestTimeout;
    private final boolean ignoreErrors;
    private final String proxy;
    private final String proxyUser;
    private final char[] proxyPassword;
    private final boolean debug;
    private final boolean followRedirects;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final boolean strictSsl;
    private final String trustStorePath;
    private final String trustStorePassword;


    private Configuration(RequestMethodType methodType,
                          String url,
                          String authToken,
                          RequestType requestType,
                          ResponseType responseType,
                          String workDir,
                          Map<String, String> requestHeaders,
                          Object body,
                          int connectTimeout,
                          int socketTimeout,
                          int requestTimeout,
                          boolean ignoreErrors,
                          String proxy,
                          String proxyUser,
                          char[] proxyPassword,
                          boolean debug,
                          boolean followRedirects,
                          String keyStorePath,
                          String keyStorePassword,
                          boolean strictSsl,
                          String trustStorePath,
                          String trustStorePassword) {

        this.methodType = methodType;
        this.url = url;
        this.encodedAuthToken = authToken;
        this.requestType = requestType;
        this.responseType = responseType;
        this.workDir = workDir;
        this.requestHeaders = requestHeaders;
        this.body = body;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        this.requestTimeout = requestTimeout;
        this.ignoreErrors = ignoreErrors;
        this.proxy = proxy;
        this.proxyUser = proxyUser;
        this.proxyPassword = proxyPassword;
        this.debug = debug;
        this.followRedirects = followRedirects;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.strictSsl = strictSsl;
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * Method to get a new instance of builder
     *
     * @return new instance of builder
     */
    public static Builder custom() {
        return new Builder();
    }

    /**
     * Method to get the method type
     *
     * @return RequestMethodType methodType
     */
    public RequestMethodType getMethodType() {
        return methodType;
    }

    /**
     * Method to get the encoded auth token
     *
     * @return encoded auth token
     */
    public String getEncodedAuthToken() {
        return encodedAuthToken;
    }

    /**
     * Method to get the url
     *
     * @return url
     */
    public String getUrl() {
        return url;
    }

    /**
     * Method to get the body
     *
     * @return body {@link Object}
     */
    public Object getBody() {
        return body;
    }

    /**
     * Method to get the request type
     *
     * @return RequestType requestType
     */
    @SuppressWarnings("unused")
    public RequestType getRequestType() {
        return requestType;
    }

    /**
     * Method to get the response type
     *
     * @return ResponseType responseType
     */
    public ResponseType getResponseType() {
        return responseType;
    }

    /**
     * Method to get the working directory
     *
     * @return working directory
     */
    public String getWorkDir() {
        return workDir;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public String getProxy() {
        return proxy;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public char[] getProxyPassword() {
        return proxyPassword;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public String keyStorePath() {
        return keyStorePath;
    }

    public String keyStorePassword() {
        return keyStorePassword;
    }

    public boolean isStrictSsl() {
        return strictSsl;
    }

    public String trustStorePath() {
        return trustStorePath;
    }

    public String trustStorePassword() {
        return trustStorePassword;
    }

    public static class Builder {

        private String url;
        private String encodedAuthToken;
        private RequestType requestType;
        private ResponseType responseType;
        private String workDir;
        private RequestMethodType methodType = RequestMethodType.GET;
        private Map<String, String> requestHeaders;
        private Object body;
        private Integer connectTimeout = 30000;
        private Integer socketTimeout = -1;
        private Integer requestTimeout = 0;
        private boolean ignoreErrors;
        private String proxy;
        private String proxyUser;
        private char[] proxyPassword;
        private boolean debug;
        private boolean followRedirects = true;
        private String keyStorePath;
        private String keyStorePassword;
        private boolean strictSsl = false;
        private String trustStorePath;
        private String trustStorePassword;

        /**
         * Used to specify the url which will later use to create {@link org.apache.http.client.methods.HttpUriRequest}
         *
         * @param url url
         * @return instance of this {@link Builder}
         */
        public Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        /**
         * Used to specify the method type
         *
         * @param methodType Http request methods
         * @return insance of this {@link Builder}
         */
        public Builder withMethodType(RequestMethodType methodType) {
            this.methodType = methodType;
            return this;
        }

        /**
         * Used to specify the encoded authentication token which later use in the Authorization
         * Header
         *
         * @param encodedAuthToken Base64 encoded string
         * @return instance of this {@link Builder}
         */
        public Builder withEncodedAuthToken(String encodedAuthToken) {
            this.encodedAuthToken = encodedAuthToken;
            return this;
        }

        /**
         * Used to specify the request type which later maps to Content-Type header of the request.
         *
         * @param requestType {@link RequestType} type of request (file, json, string)
         * @return instance of this {@link Builder}
         */
        public Builder withRequestType(RequestType requestType) {
            this.requestType = requestType;
            return this;
        }

        /**
         * Used to specify the response type, which later use to parse the response from endpoint.
         *
         * @param responseType {@link ResponseType} type of the response (file, json, string)
         * @return instance of this {@link Builder}
         */
        public Builder withResponseType(ResponseType responseType) {
            this.responseType = responseType;
            return this;
        }

        /**
         * Used to specify the working directory. This will be used to store the http response in temporary file
         *
         * @param workDir current working directory
         * @return instance of this {@link Builder}
         */
        public Builder withWorkingDirectory(String workDir) {
            this.workDir = workDir;
            return this;
        }

        /**
         * Used to specify the body
         *
         * @param body complex(map), raw body or relative path
         * @return instance of this {@link Builder}
         */
        public Builder withBody(Object body) {
            this.body = body;
            return this;
        }

        /**
         * Used to specify the connection timeout (in ms).
         * A timeout value of zero is interpreted as an infinite timeout.
         * A negative value is interpreted as undefined (system default).
         * <p>
         * Default value is {@code 30000}
         * </p>
         *
         * @param connectTimeout
         * @return instance of this {@link Builder}
         */
        public Builder withConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Used to specify the socket timeout (in ms).
         * A timeout value of zero is interpreted as an infinite timeout.
         * A negative value is interpreted as undefined (system default).
         * <p>
         * Default value is {@code -1}
         * </p>
         *
         * @param socketTimeout
         * @return instance of this {@link Builder}
         */
        public Builder withSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        /**
         * Used to specify the request timeout (in ms).
         * A timeout value of zero is interpreted as an infinite timeout.
         *
         * @param requestTimeout
         * @return instance of this {@link Builder}
         */
        public Builder withRequestTimeout(int requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Used to ignore the errors produced by the http task in flow
         *
         * @param ignoreErrors
         * @return instance of this {@link Builder}
         */
        public Builder withIgnoreErrors(boolean ignoreErrors) {
            this.ignoreErrors = ignoreErrors;
            return this;
        }

        public Builder withProxy(String proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder withDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder withFollowRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public Builder withKeyStore(String path, String password) {
            this.keyStorePath = path;
            this.keyStorePassword = password;
            return this;
        }

        public Builder setStrictSsl(boolean strictSsl) {
            this.strictSsl = strictSsl;
            return this;
        }

        public Builder withTrustStore(String path, String password) {
            this.trustStorePath = path;
            this.trustStorePassword = password;
            return this;
        }

        /**
         * Invoking this method will result in a new configuration
         *
         * @return new instance of this {@link Configuration}
         */
        public Configuration build() {
            if (this.url == null || this.url.isEmpty()) {
                throw new IllegalArgumentException("URL is missing");
            } else if (responseType == ResponseType.FILE && (workDir == null || workDir.isEmpty())) {
                throw new IllegalArgumentException("Working directory is mandatory for ResponseType FILE");
            } else if (this.methodType == RequestMethodType.POST && (this.body == null)) {
                throw new IllegalArgumentException("Body is missing for Post method");
            } else if (this.methodType == RequestMethodType.PUT && (this.body == null)) {
                throw new IllegalArgumentException("Body is missing for Put method");
            }

            return new Configuration(methodType, url, encodedAuthToken, requestType, responseType, workDir,
                    requestHeaders, body, connectTimeout, socketTimeout, requestTimeout, ignoreErrors,
                    proxy, proxyUser, proxyPassword, debug, followRedirects,
                    keyStorePath, keyStorePassword, strictSsl, trustStorePath, trustStorePassword);
        }

        /**
         * Invoking this method will result in a new configuration.
         *
         * @param ctx context use to build the configuration
         * @return new instance of this {@link Configuration}
         * @throws Exception
         */
        public Configuration build(Context ctx) throws Exception {
            String workDir = (String) ctx.getVariable(Constants.Context.WORK_DIR_KEY);

            Map<String, Object> input = new HashMap<>(ALL_KEYS.length);
            for (String k : ALL_KEYS) {
                Object v = ctx.getVariable(k);
                if (v != null) {
                    input.put(k, v);
                }
            }

            return build(workDir, input, false);
        }

        @SuppressWarnings("unchecked")
        public Configuration build(String workDir, Map<String, Object> input, boolean globalDebug) throws Exception {
            validateMandatory(input);

            this.url = MapUtils.getString(input, URL_KEY);

            // query params are optional
            Map<String, Object> queryParams = MapUtils.getMap(input, QUERY_KEY, null);
            if (queryParams != null) {
                URIBuilder uriBuilder = new URIBuilder(url);
                queryParams.forEach((k, v) -> {
                    if (v instanceof Collection) {
                        ((Collection<Object>) v).forEach(item -> uriBuilder.addParameter(k, item.toString()));
                    } else {
                        uriBuilder.setParameter(k, v.toString());
                    }
                });

                this.url = uriBuilder.build().toURL().toString();
            }

            // method param is optional
            String method = MapUtils.getString(input, METHOD_KEY, null);
            if (method != null) {
                if (RequestMethodType.isMember(method)) {
                    this.methodType = RequestMethodType.valueOf(method.toUpperCase());
                } else {
                    throw new IllegalArgumentException("'" + METHOD_KEY + ": " + method + "' is not a supported HTTP method");
                }
            }

            // auth param is optional
            Map<String, Object> authParams = MapUtils.getMap(input, AUTH_KEY, null);
            if (authParams != null) {
                this.encodedAuthToken = HttpTaskUtils.getBasicAuthorization(MapUtils.assertMap(authParams, BASIC_KEY));
            }

            // request param is optional
            String request = MapUtils.getString(input, REQUEST_KEY);
            if (request != null) {
                if (RequestType.isMember(request)) {
                    this.requestType = RequestType.valueOf(request.toUpperCase());
                } else {
                    throw new IllegalArgumentException("'" + REQUEST_KEY + ": " + request + "' is not a supported request type");
                }
            }

            // response param is optional
            String response = MapUtils.getString(input, RESPONSE_KEY);
            if (response != null) {
                if (ResponseType.isMember(response)) {
                    this.responseType = ResponseType.valueOf(response.toUpperCase());
                } else {
                    throw new IllegalArgumentException("'" + RESPONSE_KEY + ": " + response + "' is not a supported response type");
                }
            }

            if (responseType == ResponseType.FILE && (workDir == null || workDir.isEmpty())) {
                throw new IllegalArgumentException("Working directory is mandatory for ResponseType FILE");
            }

            this.requestHeaders = MapUtils.getMap(input, HEADERS_KEY, null);

            this.body = input.get(BODY_KEY);

            this.ignoreErrors = MapUtils.getBoolean(input, IGNORE_ERRORS_KEY, false);

            this.connectTimeout = MapUtils.getInt(input, CONNECT_TIMEOUT_KEY, 0);

            this.socketTimeout = MapUtils.getInt(input, SOCKET_TIMEOUT_KEY, 0);

            this.requestTimeout = MapUtils.getInt(input, REQUEST_TIMEOUT_KEY, 0);

            this.proxy = MapUtils.getString(input, PROXY_KEY);
            Map<String, Object> proxyAuth = MapUtils.getMap(input, PROXY_AUTH_KEY, Collections.emptyMap());
            this.proxyUser = MapUtils.getString(proxyAuth, PROXY_USER_KEY);
            this.proxyPassword = Optional.ofNullable(MapUtils.getString(proxyAuth, PROXY_PASSWORD_KEY))
                    .map(String::toCharArray)
                    .orElse(null);

            this.debug = MapUtils.getBoolean(input, DEBUG_KEY, globalDebug);

            this.followRedirects = MapUtils.getBoolean(input, FOLLOW_REDIRECTS_KEY, true);

            this.keyStorePath = MapUtils.getString(input, KEYSTORE_PATH);
            this.keyStorePassword = MapUtils.getString(input, KEYSTORE_PASSWD);

            this.strictSsl = MapUtils.getBoolean(input, STRICT_SSL, false);

            this.trustStorePath = MapUtils.getString(input, TRUSTSTORE_PATH);
            this.trustStorePassword = MapUtils.getString(input, TRUSTSTORE_PASSWD);

            return new Configuration(methodType, url, encodedAuthToken, requestType, responseType, workDir,
                    requestHeaders, body, connectTimeout, socketTimeout, requestTimeout, ignoreErrors,
                    proxy, proxyUser, proxyPassword, debug, followRedirects,
                    keyStorePath, keyStorePassword, strictSsl, trustStorePath, trustStorePassword);
        }

        private static void validateMandatory(Map<String, Object> m) {
            if (m.get(URL_KEY) == null) {
                throw new IllegalArgumentException("('" + URL_KEY + "') argument is missing");
            } else if (POST_METHOD.equals(m.get(METHOD_KEY)) && m.get(REQUEST_KEY) == null) {
                throw new IllegalArgumentException("('" + REQUEST_KEY + "') argument is missing for ('" + POST_METHOD + "') method");
            } else if (POST_METHOD.equals(m.get(METHOD)) && m.get(BODY_KEY) == null) {
                throw new IllegalArgumentException("('" + BODY_KEY + "') argument is missing for ('" + POST_METHOD + "') method");
            } else if (PUT_METHOD.equals(m.get(METHOD_KEY)) && m.get(REQUEST_KEY) == null) {
                throw new IllegalArgumentException("('" + REQUEST_KEY + "') argument is missing for ('" + PUT_METHOD + "') method");
            } else if (PUT_METHOD.equals(m.get(METHOD)) && m.get(BODY_KEY) == null) {
                throw new IllegalArgumentException("('" + BODY_KEY + "') argument is missing for ('" + PUT_METHOD + "') method");
            }
        }
    }
}
