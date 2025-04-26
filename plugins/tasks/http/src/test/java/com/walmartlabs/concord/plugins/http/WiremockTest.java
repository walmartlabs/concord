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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public abstract class WiremockTest {

    private WireMockServer wireMockServer;

    @TempDir
    protected Path workDir;

    @BeforeEach
    public void setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort());
        wireMockServer.start();

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
        stubForFollowRedirect();
        stubForFollowRedirectPost();
    }

    @AfterEach
    public void tearDown() {
        wireMockServer.shutdown();
    }

    protected WireMockServer wireMockServer() {
        return this.wireMockServer;
    }

    protected Configuration initCfgForRequest(String requestMethod, String requestType,
                                              String responseType, String url,
                                              boolean ignoreErrors, int requestTimeout,
                                              boolean followRedirects) throws Exception {

        return initCfgForRequest(requestMethod, requestType, responseType, url,
                ignoreErrors, requestTimeout, followRedirects, Map.of());
    }

    protected Configuration initCfgForRequest(String requestMethod, String requestType,
                                              String responseType, String url,
                                              boolean ignoreErrors, int requestTimeout,
                                              boolean followRedirects, Map<String, Object> extra) throws Exception {

        Map<String, Object> input = new HashMap<>();
        input.put("url", url);
        input.put("method", requestMethod);
        input.put("request", requestType);
        input.put("response", responseType);
        input.put("ignoreErrors", ignoreErrors);
        input.put("requestTimeout", requestTimeout);
        input.put("followRedirects", followRedirects);

        if (extra != null) {
            input.putAll(extra);
        }

        return Configuration.custom()
                .build(workDir.toString(), input, false);
    }

    protected void stubForJsonResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("""
                                [
                                    {
                                        "id": 1,
                                        "version": "1.0"
                                    },
                                    {
                                        "id": 2,
                                        "test": "1.1"
                                    }
                                ]"""))
        );
    }

    protected void stubForInvalidJsonResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/invalid/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody(""))
        );
    }

    protected void stubForFault() {
        wireMockServer.stubFor(get(urlEqualTo("/fault"))
                .willReturn(aResponse()
                        .withFault(Fault.EMPTY_RESPONSE)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Accept", "text/plain"))
        );
    }

    protected void stubForStringResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/string"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Accept", "text/plain")
                        .withBody("Test"))
        );
    }

    protected void stubForPostRequest() {
        wireMockServer.stubFor(post(urlEqualTo("/post"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("""
                                {
                                  "message": "Success"
                                }"""))
        );
    }

    protected void stubForGetSecureEndpoint() {
        wireMockServer.stubFor(get(urlEqualTo("/secure"))
                .withBasicAuth("cn=test", "password")
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized"))
        );
    }

    protected void stubForGetWithQueryParams() {
        wireMockServer.stubFor(get(urlPathEqualTo("/query"))
                .withQueryParam("key", equalTo("value with space"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Success response"))
        );
    }

    protected void stubForPostSecureEndpoint() {
        wireMockServer.stubFor(post(urlEqualTo("/secure"))
                .withBasicAuth("cn=test", "password")
                .willReturn(aResponse()
                        .withStatus(201)
                        .withBody("Success response"))
        );
    }

    protected void stubForPostRequestForRequestTypeFile() {
        wireMockServer.stubFor(post(urlEqualTo("/file"))
                .withHeader("Content-Type", equalTo("application/octet-stream"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "testObject": {
                                    "testString": "hello",
                                    "testInteger": "2"
                                  }
                                }"""))
        );
    }


    protected void stubForGetRequestForResponseTypeStringFile() {
        wireMockServer.stubFor(get(urlEqualTo("/stringFile"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("file.bin"))
        );
    }

    protected void stubForGetRequestForResponseTypeStringFileWithNoFilename() {
        wireMockServer.stubFor(get(urlEqualTo("/fileUrlWithoutName/"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("file.bin"))
        );
    }

    protected void stubForGetRequestForResponseTypeJSONFile() {
        wireMockServer.stubFor(get(urlEqualTo("/JSONFile"))
                .withHeader("Accept", equalTo("*/*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBodyFile("jsonFile.bin"))
        );
    }


    protected void stubForUnsuccessfulResponse() {
        wireMockServer.stubFor(get(urlEqualTo("/unsuccessful"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("Error string"))
        );
    }

    protected void stubForDeleteRequest() {
        wireMockServer.stubFor(delete(urlEqualTo("/delete"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("""
                                {
                                  "message": "Success"
                                }"""))
        );
    }

    protected void stubForPatchRequest() {
        wireMockServer.stubFor(patch(urlEqualTo("/patch"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Accept", "application/json")
                        .withBody("""
                                {
                                  "message": "Success"
                                }"""))
        );
    }

    protected void stubForRequestTimeout() {
        wireMockServer.stubFor(get(urlEqualTo("/requestTimeout"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withHeader("Accept", "text/plain")
                        .withBody("Request timeout")
                        .withFixedDelay(8000))
        );
    }

    protected void stubForFollowRedirect() {
        wireMockServer.stubFor(get(urlEqualTo("/followRedirects"))
                .willReturn(permanentRedirect(url(wireMockServer(), "/string")))
        );
    }

    protected void stubForFollowRedirectPost() {
        wireMockServer.stubFor(post(urlEqualTo("/followRedirectsPost"))
                .willReturn(permanentRedirect(url(wireMockServer(), "/string")))
        );
    }

    private static String url(WireMockServer wireMockServer, String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return String.format("%s%s", "http://localhost:" + wireMockServer.port(), path);
    }
}
