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

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.walmartlabs.concord.plugins.http.exception.RequestTimeoutException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleHttpClientTest extends WiremockTest {

    @Test
    void testExecuteGetRequestForJson() throws Exception {
        var config = initCfgForRequest("GET", "JSON", "JSON",
                "http://localhost:" + wireMockServer().port() + "/json", false, 0, true);

        var response = SimpleHttpClient.create(config, false)
                .execute()
                .getResponse();

        wireMockServer().verify(getRequestedFor(urlEqualTo("/json")));
        assertEquals(200, response.get("statusCode"));
        assertNull(response.get("errorString"));
    }

    @Test
    void testExecuteGetRequestForString() throws Exception {
        var config = initCfgForRequest("GET", "STRING", "STRING",
                "http://localhost:" + wireMockServer().port() + "/string", false, 0, true);
        SimpleHttpClient.create(config, false)
                .execute();
        wireMockServer().verify(getRequestedFor(urlEqualTo("/string")));
    }

    @Test
    void testExecuteGetRequestWithQueryParams() throws Exception {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("key", "value with space");

        var config = initCfgForRequest("GET", null, "STRING",
                "http://localhost:" + wireMockServer().port() + "/query", false, 0, true,
                Map.of("query", queryParams ));
        SimpleHttpClient.create(config, false)
                .execute();
        wireMockServer().verify(getRequestedFor(urlPathEqualTo("/query")).withQueryParam("key", equalTo("value with space")));
    }

    @Test
    void testExecutePostRequestWithFormUrlEncoded() throws Exception {
        var config = initCfgForRequest("POST", "form", "json",
                "http://localhost:" + wireMockServer().port() + "/post", false, 0, true,
                Map.of("body", Map.of("message", "Hello Concord!")));
        SimpleHttpClient.create(config, false)
                        .execute();

        var req = wireMockServer().getAllServeEvents().get(0).getRequest();
        assertEquals(RequestMethod.POST, req.getMethod());
        assertEquals("message=" + URLEncoder.encode("Hello Concord!", StandardCharsets.UTF_8), req.getBodyAsString());
    }

    @Test
    void testExecutePostRequestForJson() throws Exception {
        var cfg = initCfgForRequest("POST", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/post", false, 0, true,
                Map.of("body", "{ \"request\": \"PostTest\" }"));
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        wireMockServer().verify(postRequestedFor(urlEqualTo("/post"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    void testExecutePostRequestForComplexObject() throws Exception {
        HashMap<String, Object> complexObject = new HashMap<>();
        HashMap<String, Object> nestedObject = new HashMap<>();
        nestedObject.put("nestedVar", 123);
        complexObject.put("myObject", nestedObject);

        var cfg = initCfgForRequest("POST", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/post", false, 0, true,
                Map.of("body", complexObject));
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        wireMockServer().verify(postRequestedFor(urlEqualTo("/post"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    void testExecutePostRequestForMultipart() throws Exception {
        HashMap<String, Object> formInput = new HashMap<>();
        HashMap<String, Object> field = new HashMap<>();
        field.put("type", "text/plain");
        field.put("data", "\"string value\"");

        formInput.put("field1", "string value");
        formInput.put("field2", "src/test/resources/__files/file.bin");
        formInput.put("field3", field);

        var cfg = initCfgForRequest("POST", "formData", "json",
                "http://localhost:" + wireMockServer().port() + "/post", false, 0, true,
                Map.of("body", formInput));

        var out = SimpleHttpClient.create(cfg, false).execute();

        assertNotNull(out.getResponse());
        assertTrue((Boolean) out.getResponse().get("success"));
    }

    @Test
    void testExecuteForException() throws Exception {
        var cfg = initCfgForRequest("GET", "string", "string",
                "http://localhost:" + wireMockServer().port() + "/fault", false, 0, true);
        var client = SimpleHttpClient.create(cfg, false);
        var ex = assertThrows(Exception.class, client::execute);

        assertTrue(ex.getMessage().contains("failed to respond"));
    }

    @Test
    void testExecuteWithIgnoreError() throws Exception {
        var throwingCfg = initCfgForRequest("GET", "string", "string",
                "http://localhost:" + wireMockServer().port() + "/fault", false, 0, true);
        var throwingClient = SimpleHttpClient.create(throwingCfg, false);
        assertThrows(Exception.class, throwingClient::execute);

        var ignoringCfg = initCfgForRequest("GET", "string", "string",
                "http://localhost:" + wireMockServer().port() + "/fault", true, 0, true);
        var ignoringClient = SimpleHttpClient.create(ignoringCfg, false);
        assertDoesNotThrow(ignoringClient::execute);
        wireMockServer().verify(8, getRequestedFor(urlEqualTo("/fault")));
    }

    @Test
    void testExecuteGetRequestWithoutFollowRedirect() throws Exception {
        var cfg = initCfgForRequest("GET", "string", "string",
                "http://localhost:" + wireMockServer().port() + "/followRedirects", false, 0, false);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertEquals(301, out.getResponse().get("statusCode"));
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/followRedirects")));
        wireMockServer().verify(0, getRequestedFor(urlEqualTo("/string")));
    }

    @Test
    void testExecuteGetRequestWithFollowRedirect() throws Exception {
        var cfg = initCfgForRequest("GET", "string", "string",
                "http://localhost:" + wireMockServer().port() + "/followRedirects", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertEquals(200, out.getResponse().get("statusCode"));
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/followRedirects")));
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/string")));
    }

    @Test
    void testExecutePostRequestWithoutFollowRedirect() throws Exception {
        var cfg = initCfgForRequest("POST", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/followRedirectsPost", false, 0, false,
                Map.of("body", "{}"));
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertEquals(301, out.getResponse().get("statusCode"));
        wireMockServer().verify(1, postRequestedFor(urlEqualTo("/followRedirectsPost")));
        wireMockServer().verify(0, postRequestedFor(urlEqualTo("/string")));
    }

    @Test
    void testExecutePostRequestWithFollowRedirect() throws Exception {
        var cfg = initCfgForRequest("POST", "json", "string",
                "http://localhost:" + wireMockServer().port() + "/followRedirectsPost", false, 0, true,
                Map.of("body", "{}"));
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertEquals(200, out.getResponse().get("statusCode"));
        wireMockServer().verify(1, postRequestedFor(urlEqualTo("/followRedirectsPost")));
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/string")));
    }

    @Test
    void testIllegalArgumentExceptionForRequest() {
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest("GET", null, "json",
                null, false, 0, true));

        assertTrue(ex.getMessage().contains("('url') argument is missing"));
    }

    @Test
    void testGetAsDefaultRequestMethod() throws Exception {
        var cfg = initCfgForRequest(null, "json", "json",
                "http://localhost:" + wireMockServer().port() + "/json", false, 0, true);
        SimpleHttpClient.create(cfg, false)
                .execute();

        assertEquals(HttpTask.RequestMethodType.GET, cfg.getMethodType());
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/json")));
    }

    @Test
    void testGetRequestForResponseContent() throws Exception {
        var cfg = initCfgForRequest("GET", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/json", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        assertNotNull(out.getResponse().get("content"));
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/json")));
    }

    @Test
    void testUnsuccessfulResponse() throws Exception {
        var cfg = initCfgForRequest("GET", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/unsuccessful", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        assertFalse((Boolean) out.getResponse().get("success"));
        String errorString = assertInstanceOf(String.class, out.getResponse().get("errorString"));
        assertFalse(errorString.isEmpty());
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/unsuccessful")));
    }

    @Test
    void testFilePostRequest() throws Exception {
        var cfg = initCfgForRequest("POST", "file", "json",
                "http://localhost:" + wireMockServer().port() + "/file", false, 0, true,
                Map.of("body", "src/test/resources/__files/file.bin"));
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        assertTrue((Boolean) out.getResponse().get("success"));
        wireMockServer().verify(1, postRequestedFor(urlEqualTo("/file")));
    }

    @Test
    void testForMissingWorkDirForFileGetRequest() throws Exception {
        // Working directory is mandatory for response type file
        workDir = Paths.get("");
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest("GET", "json", "file",
                "http://localhost:" + wireMockServer().port() + "/stringFile", false, 0, true));

        assertTrue(ex.getMessage().contains("Working directory is mandatory for ResponseType FILE"));
    }

    @Test
    void testFileGetRequestWithWorkDir() throws Exception {
        var cfg = initCfgForRequest("GET", "json", "file",
                "http://localhost:" + wireMockServer().port() + "/stringFile", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        assertTrue((Boolean) out.getResponse().get("success"));
        assertEquals("stringFile", (new File((String) out.getResponse().get("content"))).getName());
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/stringFile")));
    }

    @Test
    void testFileGetRequestWithNoFilename() throws Exception {
        var cfg = initCfgForRequest("GET", "json", "file",
                "http://localhost:" + wireMockServer().port() + "/fileUrlWithoutName/", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        assertTrue((Boolean) out.getResponse().get("success"));
        assertTrue(((String) out.getResponse().get("content")).matches(".*/tmpfile_.*\\.tmp$"));
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/fileUrlWithoutName/")));
    }

    @Test
    void testFileGetWithResponseTypeString() throws Exception {
        var cfg = initCfgForRequest("GET", "json", "string",
                "http://localhost:" + wireMockServer().port() + "/stringFile", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/stringFile")));
    }

    @Test
    void testFileGetWithResponseTypeJSON() throws Exception {
        var cfg = initCfgForRequest("GET", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/JSONFile", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertNotNull(out.getResponse());
        assertTrue((Boolean) out.getResponse().get("success"));
        wireMockServer().verify(1, getRequestedFor(urlEqualTo("/JSONFile")));
    }

    @Test
    void testPostJsonRequestForIncompatibleBody() throws Exception {
        var cfg = initCfgForRequest("POST", "json", "string",
                "http://localhost:" + wireMockServer().port() + "/post", false, 0, true,
                Map.of("body", "src/test/resources/__files/file.bin"));
        var ex = assertThrows(IllegalArgumentException.class, () -> SimpleHttpClient.create(cfg, false));

        assertTrue(ex.getMessage().contains("'request: json' is not compatible with 'body'"));
    }

    @Test
    void testPostStringRequestForIncompatibleComplexBody() throws Exception {
        var cfg = initCfgForRequest("POST", "string", "string",
                "http://localhost:" + wireMockServer().port() + "/post", false, 0, true,
                Map.of("body", Map.of()));
        var ex = assertThrows(IllegalArgumentException.class, () -> SimpleHttpClient.create(cfg, false));

        assertTrue(ex.getMessage().contains("'request: string' is not compatible with 'body'"));
    }

    @Test
    void testPostFileRequestForIncompatibleComplexBody() throws Exception {
        var cfg = initCfgForRequest("POST", "file", "string",
                "http://localhost:" + wireMockServer().port() + "/post", false, 0, true,
                Map.of("body", Map.of()));
        var ex = assertThrows(IllegalArgumentException.class, () -> SimpleHttpClient.create(cfg, false));

        assertTrue(ex.getMessage().contains("'request: file' is not compatible with 'body'"));
    }

    @Test
    void testInvalidRequestMethodType() {
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest("GET1", "json", "file",
                "http://localhost:" + wireMockServer().port() + "/file", false, 0, true));

        assertTrue(ex.getMessage().contains("'method: GET1' is not a supported HTTP method"));
    }

    @Test
    void testInvalidRequestType() {
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest("GET", "json1", "file",
                "http://localhost:" + wireMockServer().port() + "/file", false, 0, true));

        assertTrue(ex.getMessage().contains("'request: json1' is not a supported request type"));
    }

    @Test
    void testInvalidResponseType() {
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest("GET", "json", "file1",
                "http://localhost:" + wireMockServer().port() + "/file", false, 0, true));

        assertTrue(ex.getMessage().contains("'response: file1' is not a supported response type"));
    }

    @Test
    void testOptionalResponseType() throws Exception {
        var cfg = initCfgForRequest("GET", "string", null,
                "http://localhost:" + wireMockServer().port() + "/string", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        assertTrue((Boolean) out.getResponse().get("success"));
        wireMockServer().verify(getRequestedFor(urlEqualTo("/string")));
    }

    @Test
    void testDelete() throws Exception {
        var cfg = initCfgForRequest("DELETE", "string", "json",
                "http://localhost:" + wireMockServer().port() + "/delete", false, 0, true);
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        wireMockServer().verify(deleteRequestedFor(urlEqualTo("/delete")));
        assertNotNull(out.getResponse());
        Map<?, ?> content = assertInstanceOf(Map.class, out.getResponse().get("content"));
        assertEquals("Success", content.get("message"));
    }

    @Test
    void testPatch() throws Exception {
        var cfg = initCfgForRequest("PATCH", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/patch", false, 0, true,
                Map.of("body", "{ \"request\": \"PatchTest\" }"));
        var out = SimpleHttpClient.create(cfg, false)
                .execute();

        wireMockServer().verify(patchRequestedFor(urlEqualTo("/patch")));
        assertNotNull(out.getResponse());
        Map<?, ?> content = assertInstanceOf(Map.class, out.getResponse().get("content"));
        assertEquals("Success", content.get("message"));
    }

    @Test
    void testRequestTimeoutException() throws Exception {
        var cfg = initCfgForRequest("GET", "string", "string",
                "http://localhost:" + wireMockServer().port() + "/requestTimeout", false, 250, true);

        assertThrows(RequestTimeoutException.class, () -> SimpleHttpClient.create(cfg, false)
                .execute());
    }

    @Test
    void testInvalidJsonResponse() throws Exception {
        var cfg = initCfgForRequest("GET", "json", "json",
                "http://localhost:" + wireMockServer().port() + "/invalid/json", false, 0, true);
        var client =  SimpleHttpClient.create(cfg, false);
        var ex = assertThrows(RuntimeException.class, client::execute);

        assertTrue(ex.getMessage().contains("Invalid JSON response"));
    }

    @Test
    void testInvalidRequestMethod() {
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest(null, "json", "json",
                "http://localhost:" + wireMockServer().port() + "/json", false, 0, true,
                Map.of("method", 123)));

        assertTrue(ex.getMessage().contains("Invalid variable 'method' type"));
    }

    @Test
    void testInvalidRequest() {
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest("GET", null, "json",
                "http://localhost:" + wireMockServer().port() + "/json", false, 0, true,
                Map.of("request", 123)));

        assertTrue(ex.getMessage().contains("Invalid variable 'request' type"));
    }

    @Test
    void testInvalidResponse() {
        var ex = assertThrows(IllegalArgumentException.class, () -> initCfgForRequest("GET", "json", null,
                "http://localhost:" + wireMockServer().port() + "/json", false, 0, true,
                Map.of("response", 123)));

        assertTrue(ex.getMessage().contains("Invalid variable 'response' type"));
    }

}
