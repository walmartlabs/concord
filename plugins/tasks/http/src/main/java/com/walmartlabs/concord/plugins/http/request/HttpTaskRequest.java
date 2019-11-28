package com.walmartlabs.concord.plugins.http.request;

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
import com.walmartlabs.concord.plugins.http.Version;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.*;

import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Implementation of {@link Request} use to create Http request object
 */
public class HttpTaskRequest implements Request {

    private HttpUriRequest request;

    private HttpTaskRequest(HttpUriRequest request) {
        this.request = request;
        this.request.setHeader(HttpHeaders.USER_AGENT, "Concord (http-task version '" + Version.get() + "')");
    }

    /**
     * Method to create {@link HttpDelete} request
     *
     * @param url Endpoint
     * @return HttpTaskRequest
     */
    public static HttpTaskRequest delete(String url) {
        return new HttpTaskRequest(new HttpDelete(url));
    }

    /**
     * Method to create {@link HttpPost} request
     *
     * @param url Endpoint
     * @return HttpTaskRequest
     */
    public static HttpTaskRequest post(String url) {
        return new HttpTaskRequest(new HttpPost(url));
    }

    /**
     * Method to create {@link HttpPut} request
     *
     * @param url Endpoint
     * @return HttpTaskRequest
     */
    public static HttpTaskRequest put(String url) {
        return new HttpTaskRequest(new HttpPut(url));
    }

    /**
     * Method to create {@link HttpPatch} request
     *
     * @param url Endpoint
     * @return HttpTaskRequest
     */
    public static HttpTaskRequest patch(String url) {
        return new HttpTaskRequest(new HttpPatch(url));
    }

    /**
     * Method to create {@link HttpGet} request
     *
     * @param url Endpoint
     * @return HttpTaskRequest
     */
    public static HttpTaskRequest get(String url) {
        return new HttpTaskRequest(new HttpGet(url));
    }

    @Override
    public Request withBasicAuth(String encodedToken) {
        if (encodedToken != null) {
            this.request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedToken);
        }

        return this;
    }

    @Override
    public Request withRequestType(RequestType requestType) {
        String contentType = getContentType(requestType);
        if (contentType != null && !contentType.isEmpty()) {
            this.request.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
        }

        return this;
    }

    @Override
    public Request withResponseType(ResponseType responseType) {
        String acceptType = getAcceptType(responseType);
        if (acceptType != null && !acceptType.isEmpty()) {
            this.request.setHeader(HttpHeaders.ACCEPT, acceptType);
        }

        return this;
    }

    @Override
    public Request withHeaders(Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(this.request::setHeader);
        }
        return this;
    }

    @Override
    public Request withBody(HttpEntity body) {
        if (this.request instanceof HttpPost) {
            ((HttpPost) this.request).setEntity(body);
        } else if (this.request instanceof HttpPut) {
            ((HttpPut) this.request).setEntity(body);
        } else if (this.request instanceof HttpPatch) {
            ((HttpPatch) this.request).setEntity(body);
        }

        return this;
    }

    @Override
    public HttpUriRequest get() {
        return this.request;
    }

    /**
     * Method to get the Content-Type header using the given request type
     *
     * @param requestType type of request
     * @return Specific {@link MediaType} or null in case of invalid {@link RequestType}
     */
    private String getContentType(RequestType requestType) {
        if (RequestType.JSON == requestType) {
            return MediaType.APPLICATION_JSON;
        } else if (RequestType.FILE == requestType) {
            return MediaType.APPLICATION_OCTET_STREAM;
        } else if (RequestType.STRING == requestType) {
            return MediaType.TEXT_PLAIN;
        } else if (RequestType.FORM == requestType) {
            return MediaType.APPLICATION_FORM_URLENCODED;
        }

        return null;
    }

    /**
     * Method to get the Accept header using the given response type
     *
     * @param responseType type of response
     * @return Specific {@link MediaType} or null in case of invalid {@link ResponseType}
     */
    private String getAcceptType(ResponseType responseType) {
        if (ResponseType.ANY == responseType) {
            return MediaType.WILDCARD;
        }

        return null;
    }


}
