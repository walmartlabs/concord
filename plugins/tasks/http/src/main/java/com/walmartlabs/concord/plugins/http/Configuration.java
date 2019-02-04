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

import com.walmartlabs.concord.plugins.http.HttpTask.RequestType;
import com.walmartlabs.concord.plugins.http.HttpTask.ResponseType;
import com.walmartlabs.concord.sdk.Context;

import java.util.Map;

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
    private final boolean ignoreErrors;
    private final String proxy;

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
                          boolean ignoreErrors,
                          String proxy) {

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
        this.ignoreErrors = ignoreErrors;
        this.proxy = proxy;
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

    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    public String getProxy() {
        return proxy;
    }

    public static class Builder {

        private String url;
        private String encodedAuthToken;
        private RequestType requestType;
        private ResponseType responseType;
        private String workDir;
        private RequestMethodType methodType;
        private Map<String, String> requestHeaders;
        private Object body;
        private Integer connectTimeout = 30000;
        private Integer socketTimeout = -1;
        private boolean ignoreErrors;
        private String proxy;

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
         *
         * @param socketTimeout
         * @return instance of this {@link Builder}
         */
        public Builder withSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
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

        /**
         * Invoking this method will result in a new configuration
         *
         * @return new instance of this {@link Configuration}
         */
        public Configuration build() {
            if (this.url == null || this.url.isEmpty()) {
                throw new IllegalArgumentException("URL is missing");
            } else if (this.methodType == null) {
                throw new IllegalArgumentException("Http request method is missing");
            } else if (responseType == ResponseType.FILE && (workDir == null || workDir.isEmpty())) {
                throw new IllegalArgumentException("Working directory is mandatory for ResponseType FILE");
            } else if (this.methodType == RequestMethodType.POST && (this.body == null)) {
                throw new IllegalArgumentException("Body is missing for Post method");
            } else if (this.methodType == RequestMethodType.PUT && (this.body == null)) {
                throw new IllegalArgumentException("Body is missing for Put method");
            }

            return new Configuration(methodType, url, encodedAuthToken, requestType, responseType, workDir, requestHeaders, body, connectTimeout, socketTimeout, ignoreErrors, proxy);
        }

        /**
         * Invoking this method will result in a new configuration
         *
         * @param ctx context use to build the configuration
         * @return new instance of this {@link Configuration}
         */
        @SuppressWarnings("unchecked")
        public Configuration build(Context ctx) {
            validateMandatory(ctx);

            this.url = (String) ctx.getVariable(URL_KEY);

            if (RequestMethodType.isMember((String) ctx.getVariable(METHOD_KEY))) {
                this.methodType = RequestMethodType.valueOf(((String) ctx.getVariable(METHOD_KEY)).toUpperCase());
            } else {
                throw new IllegalArgumentException("'" + METHOD_KEY + ": " + ctx.getVariable(METHOD_KEY) + "' is not valid");
            }

            // auth param is optional
            if (ctx.getVariable(AUTH_KEY) != null) {
                Map<String, Object> authParams = (Map<String, Object>) ctx.getVariable(AUTH_KEY);

                this.encodedAuthToken = HttpTaskUtils.getBasicAuthorization((Map<String, String>) authParams.get(BASIC_KEY));
            }

            // request param is optional
            if (ctx.getVariable(REQUEST_KEY) != null) {
                if (RequestType.isMember((String) ctx.getVariable(REQUEST_KEY))) {
                    this.requestType = RequestType.valueOf(((String) ctx.getVariable(REQUEST_KEY)).toUpperCase());
                } else {
                    throw new IllegalArgumentException("'" + REQUEST_KEY + ": " + ctx.getVariable(REQUEST_KEY) + "' is not valid");
                }
            }

            if (ResponseType.isMember((String) ctx.getVariable(RESPONSE_KEY))) {
                this.responseType = ResponseType.valueOf(((String) ctx.getVariable(RESPONSE_KEY)).toUpperCase());
            } else {
                throw new IllegalArgumentException("'" + RESPONSE_KEY + ": " + ctx.getVariable(RESPONSE_KEY) + "' is not valid");
            }

            this.workDir = (String) ctx.getVariable("workDir");

            if (responseType == ResponseType.FILE && (workDir == null || workDir.isEmpty())) {
                throw new IllegalArgumentException("Working directory is mandatory for ResponseType FILE");
            }

            this.requestHeaders = (Map<String, String>) ctx.getVariable(HEADERS_KEY);

            this.body = ctx.getVariable(BODY_KEY);

            if (ctx.getVariable(CONNECT_TIMEOUT_KEY) != null) {
                this.connectTimeout = (Integer) ctx.getVariable(CONNECT_TIMEOUT_KEY);
            }

            if (ctx.getVariable(SOCKET_TIMEOUT_KEY) != null) {
                this.socketTimeout = (Integer) ctx.getVariable(SOCKET_TIMEOUT_KEY);
            }

            if (ctx.getVariable(IGNORE_ERRORS_KEY) != null) {
                this.ignoreErrors = (boolean) ctx.getVariable(IGNORE_ERRORS_KEY);
            }

            this.proxy = (String) ctx.getVariable(PROXY_KEY);

            return new Configuration(methodType, url, encodedAuthToken, requestType, responseType, workDir, requestHeaders, body, connectTimeout, socketTimeout, ignoreErrors, proxy);
        }

        /**
         * Method validate the mandatory arguments
         *
         * @param ctx context which contains the mandatory arguments
         */
        private void validateMandatory(Context ctx) {
            if (ctx.getVariable(METHOD_KEY) == null) {
                throw new IllegalArgumentException("('" + METHOD_KEY + "') argument is missing");
            } else if (ctx.getVariable(URL_KEY) == null) {
                throw new IllegalArgumentException("('" + URL_KEY + "') argument is missing");
            } else if (ctx.getVariable(RESPONSE_KEY) == null) {
                throw new IllegalArgumentException("('" + RESPONSE_KEY + "') argument is missing");
            } else if (REQUEST_POST_KEY.equals(ctx.getVariable(METHOD_KEY)) && ctx.getVariable(REQUEST_KEY) == null) {
                throw new IllegalArgumentException("('" + REQUEST_KEY + "') argument is missing for ('" + REQUEST_POST_KEY + "') method");
            } else if (REQUEST_POST_KEY.equals(ctx.getVariable(METHOD)) && ctx.getVariable(BODY_KEY) == null) {
                throw new IllegalArgumentException("('" + BODY_KEY + "') argument is missing for ('" + REQUEST_POST_KEY + "') method");
            } else if (REQUEST_PUT_KEY.equals(ctx.getVariable(METHOD_KEY)) && ctx.getVariable(REQUEST_KEY) == null) {
                throw new IllegalArgumentException("('" + REQUEST_KEY + "') argument is missing for ('" + REQUEST_PUT_KEY + "') method");
            } else if (REQUEST_PUT_KEY.equals(ctx.getVariable(METHOD)) && ctx.getVariable(BODY_KEY) == null) {
                throw new IllegalArgumentException("('" + BODY_KEY + "') argument is missing for ('" + REQUEST_PUT_KEY + "') method");
            }
        }
    }
}
