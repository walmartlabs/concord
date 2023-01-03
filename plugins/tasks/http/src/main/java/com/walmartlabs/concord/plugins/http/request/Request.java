package com.walmartlabs.concord.plugins.http.request;

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
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpUriRequest;

import java.util.Map;

public interface Request {

    /**
     * Method to set the Authorization header in the request
     *
     * @param encodedToken Base64 encoded token
     * @return Instance of this Request
     */
    Request withBasicAuth(String encodedToken);

    /**
     * Method to set the Content-Type header in the request
     *
     * @param requestType {@link RequestType}
     * @return Instance of this Request
     */
    Request withRequestType(RequestType requestType);

    /**
     * Method to set the response type, which will later use to parse the
     * response from endpoint
     *
     * @param responseType {@link ResponseType}
     * @return Instance of this Request
     */
    Request withResponseType(ResponseType responseType);


    /**
     * Method to set the request headers
     *
     * @param headers
     * @return Instance of this Request
     */
    Request withHeaders(Map<String, String> headers);

    /**
     * Method to set the Body of the request
     *
     * @param body request body
     * @return Instance of this Request
     */
    Request withBody(HttpEntity body);

    /**
     * Method to get the underlying {@link HttpUriRequest}
     *
     * @return HttpUriRequest
     */
    HttpUriRequest get();
}
