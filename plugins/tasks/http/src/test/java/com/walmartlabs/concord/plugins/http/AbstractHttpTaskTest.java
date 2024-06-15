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

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.walmartlabs.concord.sdk.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.stubbing.Answer;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@WireMockTest
public abstract class AbstractHttpTaskTest {

    protected HttpTask task;
    /**
     * This will be use to hold the response when {@link HttpTask} calls the {@link Context#setVariable(String, Object)}
     */
    protected Map<String, Object> response;

    @BeforeEach
    public void setup(WireMockRuntimeInfo wmRuntimeInfo) {
        task = new HttpTask();

        stubForJsonResponse();
        stubForStringResponse();
        stubForPostRequest();
        stubForGetSecureEndpoint();
        stubForGetWithQueryParams();
        stubForPostSecureEndpoint();
        stubForPostRequestForRequestTypeFile();
        stubForGetRequestForResponseTypeStringFile();
        stubForGetRequestForResponseTypeStringFileWithNoFilename();
        stubForGetRequestForResponseTypeJSONFile();
        stubForUnsuccessfulResponse();
        stubForDeleteRequest();
        stubForPatchRequest();
        stubForFault();
        stubForRequestTimeout();
        stubForInvalidJsonResponse();
        stubForFollowRedirect(wmRuntimeInfo);
        stubForFollowRedirectPost(wmRuntimeInfo);
    }

    @AfterEach
    public void tearDown() {
        response = null;
    }

    protected void initCxtForRequest(Context ctx, Object requestMethod, Object requestType, Object responseType,
                                     Object url, Object ignoreErrors, Object requestTimeout, Object followRedirects) {
        initCxtForRequest(ctx, requestMethod, null, requestType, responseType, url, ignoreErrors, requestTimeout, followRedirects);
    }

    @SuppressWarnings("unchecked")
    protected void initCxtForRequest(Context ctx, Object requestMethod, Object query, Object requestType, Object responseType,
                                     Object url, Object ignoreErrors, Object requestTimeout, Object followRedirects) {
        when(ctx.getVariable("url")).thenReturn(url);
        when(ctx.getVariable("method")).thenReturn(requestMethod);
        when(ctx.getVariable("query")).thenReturn(query);
        when(ctx.getVariable("request")).thenReturn(requestType);
        when(ctx.getVariable("response")).thenReturn(responseType);
        when(ctx.getVariable("out")).thenReturn("rsp");
        when(ctx.getVariable("ignoreErrors")).thenReturn(ignoreErrors);
        when(ctx.getVariable("requestTimeout")).thenReturn(requestTimeout);
        when(ctx.getVariable("followRedirects")).thenReturn(followRedirects);

        doAnswer((Answer<Void>) invocation -> {
            response = (Map<String, Object>) invocation.getArguments()[1];
            return null;
        }).when(ctx).setVariable(anyString(), any());
    }

    protected void stubForJsonResponse() {
        stubFor(get(urlEqualTo("/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("[\n" +
                                  "    {\n" +
                                  "        \"id\": 1,\n" +
                                  "        \"version\": \"1.0\"\n" +
                                  "    },\n" +
                                  "    {\n" +
                                  "        \"id\": 2,\n" +
                                  "        \"test\": \"1.1\"\n" +
                                  "    }\n" +
                                  "]"))
        );
    }

    protected void stubForInvalidJsonResponse() {
        stubFor(get(urlEqualTo("/invalid/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody(""))
        );
    }

    protected void stubForFault() {
        stubFor(get(urlEqualTo("/fault"))
                .willReturn(aResponse()
                        .withFault(Fault.EMPTY_RESPONSE)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Accept", "text/plain"))
        );
    }

    protected void stubForStringResponse() {
        stubFor(get(urlEqualTo("/string"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Accept", "text/plain")
                        .withBody("Test"))
        );
    }

    protected void stubForPostRequest() {
        stubFor(post(urlEqualTo("/post"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("{\n" +
                                  "  \"message\": \"Success\"\n" +
                                  "}"))
        );
    }

    protected void stubForGetSecureEndpoint() {
        stubFor(get(urlEqualTo("/secure"))
                .withBasicAuth("cn=test", "password")
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized"))
        );
    }

    protected void stubForGetWithQueryParams() {
        stubFor(get(urlPathEqualTo("/query"))
                .withQueryParam("key", equalTo("value with space"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Success response"))
        );
    }

    protected void stubForPostSecureEndpoint() {
        stubFor(post(urlEqualTo("/secure"))
                .withBasicAuth("cn=test", "password")
                .willReturn(aResponse()
                        .withStatus(201)
                        .withBody("Success response"))
        );
    }

    protected void stubForPostRequestForRequestTypeFile() {
        stubFor(post(urlEqualTo("/file"))
                .withHeader("Content-Type", equalTo("application/octet-stream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                  "  \"testObject\": {\n" +
                                  "    \"testString\": \"hello\",\n" +
                                  "    \"testInteger\": \"2\"\n" +
                                  "  }\n" +
                                  "}"))
        );
    }


    protected void stubForGetRequestForResponseTypeStringFile() {
        stubFor(get(urlEqualTo("/stringFile"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("file.bin"))
        );
    }

    protected void stubForGetRequestForResponseTypeStringFileWithNoFilename() {
        stubFor(get(urlEqualTo("/fileUrlWithoutName/"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("file.bin"))
        );
    }

    protected void stubForGetRequestForResponseTypeJSONFile() {
        stubFor(get(urlEqualTo("/JSONFile"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("jsonFile.bin"))
        );
    }


    protected void stubForUnsuccessfulResponse() {
        stubFor(get(urlEqualTo("/unsuccessful"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Error string"))
        );
    }

    protected void stubForDeleteRequest() {
        stubFor(delete(urlEqualTo("/delete"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("{\n" +
                                  "  \"message\": \"Success\"\n" +
                                  "}"))
        );
    }

    protected void stubForPatchRequest() {
        stubFor(patch(urlEqualTo("/patch"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("{\n" +
                                  "  \"message\": \"Success\"\n" +
                                  "}"))
        );
    }

    protected void stubForRequestTimeout() {
        stubFor(get(urlEqualTo("/requestTimeout"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Accept", "text/plain")
                        .withBody("Request timeout")
                        .withFixedDelay(8000))
        );
    }

    protected void stubForFollowRedirect(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlEqualTo("/followRedirects"))
                .willReturn(permanentRedirect(url(wmRuntimeInfo, "/string")))
        );
    }

    protected void stubForFollowRedirectPost(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(post(urlEqualTo("/followRedirectsPost"))
                .willReturn(permanentRedirect(url(wmRuntimeInfo, "/string")))
        );
    }

    private static String url(WireMockRuntimeInfo wmRuntimeInfo, String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return String.format("%s%s", wmRuntimeInfo.getHttpBaseUrl(), path);
    }
}
