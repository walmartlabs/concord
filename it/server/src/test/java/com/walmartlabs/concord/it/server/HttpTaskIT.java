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

    private static final String mockHttpBaseUrl;
    private static final String mockHttpAuthToken = "Y249dGVzdDpwYXNzd29yZA==";
    private static final String mockHttpAuthUser = "cn=test";
    private static final String mockHttpAuthPassword = "password";
    private static final String mockHttpPathPing = "/api/v1/server/ping";
    private static final String mockHttpPathToken = "/token";
    private static final String mockHttpPathPassword = "/password";
    private static final String mockHttpPathHeaders = "/headers";
    private static final String mockHttpPathUnauthorized = "/unauthorized";
    private static final String mockHttpPathEmpty = "/empty";

    private static final String SERVER_URL;

    static {
        SERVER_URL = "http://localhost" + ":" + env("IT_SERVER_PORT", "8001");
        mockHttpBaseUrl = "http://" + env("IT_DOCKER_HOST_ADDR", "localhost") + ":";
    }

    @Rule
    public WireMockRule rule = new WireMockRule(WireMockConfiguration.options()
            .extensions(new RequestHeaders())
            .dynamicPort());

    @Before
    public void setup() {
        stubForGetAsStringEndpoint(mockHttpPathPing);
        stubForGetSecureEndpoint(mockHttpAuthUser, mockHttpAuthPassword, mockHttpPathPassword);
        stubForPostSecureEndpoint(mockHttpAuthUser, mockHttpAuthPassword, mockHttpPathPassword);
        stubForGetSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathToken);
        stubForPostSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathToken);
        stubForPatchSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathPassword);
        stubForHeadersEndpoint(mockHttpPathHeaders);
        stubForUnAuthorizedRequestEndpoint(mockHttpPathUnauthorized);
        stubForEmptyResponse(mockHttpPathEmpty);
    }

    @After
    public void tearDown() {
        rule.shutdownServer();
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetAsString() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetAsString").toURI();
        byte[] payload = archive(dir);

        ProcessApi processApi = new ProcessApi(getApiClient());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", SERVER_URL + mockHttpPathPing);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGet() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGet").toURI();
        byte[] payload = archive(dir);

        ProcessApi processApi = new ProcessApi(getApiClient());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", SERVER_URL + mockHttpPathPing);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetWithAuthUsingPassword() throws Exception {

        URI dir = HttpTaskIT.class.getResource("httpGetWithAuthUsingPassword").toURI();
        byte[] payload = archive(dir);

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetWithAuthUsingToken() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithAuthUsingToken").toURI();
        byte[] payload = archive(dir);

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testPost() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPost").toURI();
        byte[] payload = archive(dir);

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testPatch() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPatch").toURI();
        byte[] payload = archive(dir);

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testPostWithAuthUsingToken() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPostWithAuthUsingToken").toURI();
        byte[] payload = archive(dir);

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

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetWithInvalidUrl() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithInvalidUrl").toURI();
        byte[] payload = archive(dir);

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*server not exists.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testGetWithHeaders() throws Exception {

        URI dir = HttpTaskIT.class.getResource("httpGetWithHeaders").toURI();
        byte[] payload = archive(dir);

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

    private void stubForGetAsStringEndpoint(String url) {
        rule.stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"Success\": \"true\"\n" +
                                "}"))
        );
    }

    @Test(timeout = 60000)
    public void testGetWithIgnoreErrors() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithIgnoreErrors").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", "wrongUsername");
        input.put("arguments.password", "wrongPassword");
        input.put("arguments.url", mockHttpBaseUrl + rule.port() + mockHttpPathUnauthorized);
        StartProcessResponse spr = start(input);

        ProcessApi processApi = new ProcessApi(getApiClient());

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Response status code: 401*", ab);
        assertLog(".*Success response: false*", ab);
    }

    @Test(timeout = 60000)
    public void testGetEmptyResponse() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetEmpty").toURI();
        byte[] payload = archive(dir);

        ProcessApi processApi = new ProcessApi(getApiClient());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl + rule.port() + mockHttpPathEmpty);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*Success response: true.*", ab);
        assertLog(".*Content is NULL: true.*", ab);
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

    private void stubForPatchSecureTokenEndpoint(String authToken, String url) {
        rule.stubFor(patch(urlEqualTo(url))
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

    private void stubForUnAuthorizedRequestEndpoint(String url) {
        rule.stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("{\n" +
                                "  \"Authorized\": \"false\"\n" +
                                "}"))
        );
    }

    private void stubForEmptyResponse(String url) {
        rule.stubFor(get(urlEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(204)));
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
