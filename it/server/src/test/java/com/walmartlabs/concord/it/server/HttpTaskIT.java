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
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpTaskIT extends AbstractServerIT {

    private static final String mockHttpAuthToken = "Y249dGVzdDpwYXNzd29yZA==";
    private static final String mockHttpAuthUser = "cn=test";
    private static final String mockHttpAuthPassword = "password";
    private static final String mockHttpPathPing = "/api/v1/server/ping";
    private static final String mockHttpPathQueryParams = "/query";
    private static final String mockHttpPathToken = "/token";
    private static final String mockHttpPathPassword = "/password";
    private static final String mockHttpPathHeaders = "/headers";
    private static final String mockHttpPathUnauthorized = "/unauthorized";
    private static final String mockHttpPathEmpty = "/empty";
    private static final String mockHttpPathFormUrlEncoded = "/formUrlEncode";
    private static final String mockHttpPathFollowRedirects = "/followRedirects";

    @RegisterExtension
    final WireMockExtension rule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort()
                    .globalTemplating(true)
                    .extensions(new RequestHeaders()))
            .build();

    private String mockHttpBaseUrl() {
        return "http://" + concord().hostAddressAccessibleByContainers() + ":" + rule.getPort();
    }

    @BeforeEach
    public void setup() {
        org.testcontainers.Testcontainers.exposeHostPorts(rule.getPort());
        stubForGetAsStringEndpoint(mockHttpPathPing);
        stubForGetWithQueryEndpoint(mockHttpPathQueryParams);
        stubForGetSecureEndpoint(mockHttpAuthUser, mockHttpAuthPassword, mockHttpPathPassword);
        stubForPostSecureEndpoint(mockHttpAuthUser, mockHttpAuthPassword, mockHttpPathPassword);
        stubForGetSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathToken);
        stubForPostSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathToken);
        stubForPatchSecureTokenEndpoint(mockHttpAuthToken, mockHttpPathPassword);
        stubForHeadersEndpoint(mockHttpPathHeaders);
        stubForUnAuthorizedRequestEndpoint(mockHttpPathUnauthorized);
        stubForEmptyResponse(mockHttpPathEmpty);
        stubForFormUrlEncodedEndpoint(mockHttpPathFormUrlEncoded);
        stubForRedirects(mockHttpPathFollowRedirects);
    }

    @AfterEach
    public void tearDown() {
        rule.shutdownServer();
    }

    @Test
    public void testGetAsString() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetAsString").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPing);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
    }

    @Test
    public void testGet() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGet").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPing);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testGetAsDefaultMethod() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetAsDefaultMethod").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPing);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Request method: GET*", ab);
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testGetWithQueryParams() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithQueryParams").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathQueryParams);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*message: hello concord!*", ab);
        assertLog(".*multi-value-1: value1*", ab);
        assertLog(".*multi-value-2: value2*", ab);
    }

    @Test
    public void testGetWithAuthUsingPassword() throws Exception {

        URI dir = HttpTaskIT.class.getResource("httpGetWithAuthUsingPassword").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", mockHttpAuthUser);
        input.put("arguments.password", mockHttpAuthPassword);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPassword);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testGetWithAuthUsingToken() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithAuthUsingToken").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.authToken", mockHttpAuthToken);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathToken);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testPost() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPost").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", mockHttpAuthUser);
        input.put("arguments.password", mockHttpAuthPassword);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPassword);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testPostWithArrayBody() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPostArray").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", mockHttpAuthUser);
        input.put("arguments.password", mockHttpAuthPassword);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPassword);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testPostWithDebug() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPostWithDebug").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", mockHttpAuthUser);
        input.put("arguments.password", mockHttpAuthPassword);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPassword);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*requestInfo.*", ab);
        assertLog(".*responseInfo.*", ab);
    }

    @Test
    public void testPostWithFormUrlEncoded() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPostWithFormUrlEncoded").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathFormUrlEncoded);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testPatch() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPatch").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", mockHttpAuthUser);
        input.put("arguments.password", mockHttpAuthPassword);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathPassword);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testPostWithAuthUsingToken() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpPostWithAuthUsingToken").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.authToken", mockHttpAuthToken);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathToken);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
    }

    @Test
    public void testGetWithInvalidUrl() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithInvalidUrl").toURI();
        byte[] payload = archive(dir);

        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FAILED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*server not exists.*", ab);
    }

    @Test
    public void testGetWithHeaders() throws Exception {

        URI dir = HttpTaskIT.class.getResource("httpGetWithHeaders").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathHeaders);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response.*", ab);
        assertLog(".*Out Response: true*", ab);
        assertLog(".*Response content: request headers:.*h1=v1.*", ab);
        assertLog(".*Response content: request headers:.*h2=v2.*", ab);
    }

    @Test
    public void testGetWithIgnoreErrors() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetWithIgnoreErrors").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.user", "wrongUsername");
        input.put("arguments.password", "wrongPassword");
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathUnauthorized);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*statusCode: 401*", ab);
        assertLog(".*Success response: false*", ab);
    }

    @Test
    public void testGetEmptyResponse() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpGetEmpty").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathEmpty);

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Success response: true.*", ab);
        assertLog(".*Content is NULL: true.*", ab);
    }

    @Test
    public void testFollowRedirects() throws Exception {
        URI dir = HttpTaskIT.class.getResource("httpFollowRedirects").toURI();
        byte[] payload = archive(dir);

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.authToken", mockHttpAuthToken);
        input.put("arguments.url", mockHttpBaseUrl() + mockHttpPathFollowRedirects);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*Response status code: 302.*", ab);
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

    private void stubForGetWithQueryEndpoint(String url) {
        // Use response templating because of multi-value params limitation in WireMock
        rule.stubFor(get(urlPathEqualTo(url))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                  " \"message\": \"{{request.requestLine.query.message}}\", " +
                                  " \"multiValue1\": \"{{request.requestLine.query.multiValue.[0]}}\", " +
                                  " \"multiValue2\": \"{{request.requestLine.query.multiValue.[1]}}\"" +
                                  "}")
                        .withTransformers("response-template"))
        );

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

    private void stubForFormUrlEncodedEndpoint(String url) {
        rule.stubFor(post(urlEqualTo(url))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                .withRequestBody(containing("message=Hello+Concord%21"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                  "  \"Success\": \"true\"\n" +
                                  "}")));
    }

    private void stubForRedirects(String url) {
        rule.stubFor(get(urlEqualTo(url))
                .willReturn(temporaryRedirect("/temporaryRedirect")));
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
