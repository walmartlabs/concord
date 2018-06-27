package com.walmartlabs.concord.it.server;

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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.StartProcessResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertEquals;

public class HttpTaskIT extends AbstractServerIT {

    public static final String mockHttpAuthToken = "Y249dGVzdDpwYXNzd29yZA==";
    public static final String mockHttpAuthUser = "cn=test";
    public static final String mockHttpAuthPassword = "password";
    public static final String mockHttpBaseUrl = "http://localhost:";
    public static final String mockHttpPathToken = "/token";
    public static final String mockHttpPathPassword = "/password";
    public static final String mockHttpPathHeaders = "/headers";

    @Rule
    public WireMockRule rule = new WireMockRule(WireMockConfiguration.options()
            .notifier(new ConsoleNotifier(true))
            .extensions(new RequestHeaders())
            .dynamicPort());

    @Before
    public void setup() {

        stubForGetSecureEndpoint(mockHttpAuthUser, mockHttpAuthPassword, mockHttpPathPassword);
        stubForPostSecureEndpoint(mockHttpAuthUser, mockHttpAuthPassword, mockHttpPathPassword);
        stubForGetSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathToken);
        stubForPostSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathToken);
        stubForHeadersEndpoint(mockHttpPathHeaders);
    }

    @After
    public void tearDown() {
        rule.shutdownServer();
    }

    @Test(timeout = 60000)
    public void testGetAsString() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetAsString").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
    }

    @Test(timeout = 60000)
    public void testGet() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGet").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test(timeout = 60000)
    public void testGetWithAuthUsingPassword() throws Exception {

        URI dir = HttpTaskIT.class.getResource("httpGetWithAuthUsingPassword").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", mockHttpAuthUser);
        input.put("arguments.password", mockHttpAuthPassword);
        input.put("arguments.url", mockHttpBaseUrl + rule.port() + mockHttpPathPassword);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test(timeout = 60000)
    public void testGetWithAuthUsingToken() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithAuthUsingToken").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.authToken", mockHttpAuthToken);
        input.put("arguments.url", mockHttpBaseUrl + rule.port() + mockHttpPathToken);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test(timeout = 60000)
    public void testPost() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPost").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", mockHttpAuthUser);
        input.put("arguments.password", mockHttpAuthPassword);
        input.put("arguments.url", mockHttpBaseUrl + rule.port() + mockHttpPathPassword);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test(timeout = 60000)
    public void testPostWithAuthUsingToken() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPostWithAuthUsingToken").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.authToken", mockHttpAuthToken);
        input.put("arguments.url", mockHttpBaseUrl + rule.port() + mockHttpPathToken);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test(timeout = 60000)
    public void testGetWithInvalidUrl() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithInvalidUrl").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*server not exists.*", ab);
    }

    @Test(timeout = 60000)
    public void testGetWithHeaders() throws Exception {

        URI dir = HttpTaskIT.class.getResource("httpGetWithHeaders").toURI();
        byte[] payload = archive(dir, ITConstants.DEPENDENCIES_DIR);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl + rule.port() + mockHttpPathHeaders);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
        assertLog(".*Response content: request headers:.*h1=v1.*", ab);
        assertLog(".*Response content: request headers:.*h2=v2.*", ab);
    }

    private void stubForGetSecureEndpoint(String user, String password, String url) {
        rule.stubFor(get(urlEqualTo(url))
                .withBasicAuth(user, password)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"Authorized\": \"true\"\n" +
                                "}"))
        );
    }

    private void stubForGetSecureTokenEndpoint(String authToken, String url) {
        rule.stubFor(get(urlEqualTo(url))
                .withHeader("Authorization", equalTo("Basic " + authToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"Authorized\": \"true\"\n" +
                                "}"))
        );
    }

    private void stubForPostSecureEndpoint(String user, String password, String url) {
        rule.stubFor(post(urlEqualTo(url))
                .withBasicAuth(user, password)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"Authorized\": \"true\"\n" +
                                "}"))
        );
    }

    private void stubForPostSecureTokenEndpoint(String authToken, String url) {
        rule.stubFor(post(urlEqualTo(url))
                .withHeader("Authorization", equalTo("Basic " + authToken))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"Authorized\": \"true\"\n" +
                                "}"))
        );
    }

    private void stubForHeadersEndpoint(String url) {
        rule.stubFor(post(urlEqualTo(url))
                .willReturn(aResponse()
                        .withTransformers("request-headers"))
        );
    }

    public static class RequestHeaders extends ResponseDefinitionTransformer {

        @Override
        public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource files, Parameters parameters) {
            String headers = "";
            if (request.getHeaders() != null) {
                headers = request.getHeaders().all().stream()
                        .map(this::toString)
                        .collect(Collectors.joining(", "));
            }
            return new ResponseDefinitionBuilder()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("request headers: " + headers)
                    .build();
        }

        private String toString(HttpHeader h) {
            String value = "";
            if (h.isPresent()) {
                value = h.firstValue();
                if (h.values().size() > 1) {
                    value = String.join(",", h.values());
                }
            }
            return h.key() + "=" + value;
        }

        @Override
        public String getName() {
            return "request-headers";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }


}
