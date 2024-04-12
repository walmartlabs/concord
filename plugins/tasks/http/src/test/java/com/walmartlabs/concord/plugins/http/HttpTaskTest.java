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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.walmartlabs.concord.plugins.http.exception.RequestTimeoutException;
import com.walmartlabs.concord.sdk.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpTaskTest extends AbstractHttpTaskTest {

    private final Context mockContext = mock(Context.class);

    @Test
    public void testForAsStringMethod(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        String response = task.asString("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/json");
        verify(getRequestedFor(urlEqualTo("/json")));
        assertNotNull(response);
    }

    @Test
    public void testExecuteGetRequestForJson(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/json", false, 0, true);
        task.execute(mockContext);
        verify(getRequestedFor(urlEqualTo("/json")));
        assertEquals(200, response.get("statusCode"));
        assertNull(response.get("errorString"));
    }

    @Test
    public void testExecuteGetRequestForString(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "string", "string",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/string", false, 0, true);
        task.execute(mockContext);
        verify(getRequestedFor(urlEqualTo("/string")));
    }

    @Test
    public void testExecuteGetRequestWithQueryParams(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("key", "value with space");

        initCxtForRequest(mockContext, "GET", queryParams, null, "string",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/query", false, 0, true);
        task.execute(mockContext);
        verify(getRequestedFor(urlPathEqualTo("/query")).withQueryParam("key", equalTo("value with space")));
    }

    @Test
    public void testExecutePostRequestWithFormUrlEncoded(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "POST", "form", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/post", false, 0, true);
        when(mockContext.getVariable("body")).thenReturn(Collections.singletonMap("message", "Hello Concord!"));
        task.execute(mockContext);
    }

    @Test
    public void testExecutePostRequestForJson(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "POST", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/post", false, 0, true);
        when(mockContext.getVariable("body")).thenReturn("{ \"request\": \"PostTest\" }");
        task.execute(mockContext);
        verify(postRequestedFor(urlEqualTo("/post"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    public void testExecutePostRequestForComplexObject(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "POST", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/post", false, 0, true);
        HashMap<String, Object> complexObject = new HashMap<>();
        HashMap<String, Object> nestedObject = new HashMap<>();
        nestedObject.put("nestedVar", 123);
        complexObject.put("myObject", nestedObject);
        when(mockContext.getVariable("body")).thenReturn(complexObject);
        task.execute(mockContext);
        verify(postRequestedFor(urlEqualTo("/post"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    public void testExecutePostRequestForMultipart(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "POST", "formData", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/post", false, 0, true);

        HashMap<String, Object> formInput = new HashMap<>();
        HashMap<String, Object> field = new HashMap<>();
        field.put("type", "text/plain");
        field.put("data", "\"string value\"");

        formInput.put("field1", "string value");
        formInput.put("field2", "src/test/resources/__files/file.bin");
        formInput.put("field3", field);
        when(mockContext.getVariable("body")).thenReturn(formInput);
        task.execute(mockContext);

        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
    }

    @Test
    public void testExecuteForException(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        assertThrows(Exception.class, () -> {
            initCxtForRequest(mockContext, "GET", "string", "string",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/fault", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testExecuteWithIgnoreError(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "string", "string",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/fault", true, 0, true);
        task.execute(mockContext);
    }

    @Test
    public void testExecuteGetRequestWithoutFollowRedirect(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "string", "string",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/followRedirects", false, 0, false);
        task.execute(mockContext);
        assertEquals(301, response.get("statusCode"));
    }

    @Test
    public void testExecuteGetRequestWithFollowRedirect(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "string", "string",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/followRedirects", false, 0, true);
        task.execute(mockContext);
        assertEquals(200, response.get("statusCode"));
    }

    @Test
    public void testExecutePostRequestWithoutFollowRedirect(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "POST", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/followRedirectsPost", false, 0, false);
        when(mockContext.getVariable("body")).thenReturn("{}");
        task.execute(mockContext);
        assertEquals(301, response.get("statusCode"));
    }

    @Test
    public void testExecutePostRequestWithFollowRedirect(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "POST", "json", "string",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/followRedirectsPost", false, 0, true);
        when(mockContext.getVariable("body")).thenReturn("{}");
        task.execute(mockContext);
        assertEquals(200, response.get("statusCode"));
    }

    @Test
    public void testIllegalArgumentExceptionForRequest() {
        assertThrows(IllegalArgumentException.class, () -> {
            task.execute(mockContext);
        });
    }

    @Test
    public void testGetAsDefaultRequestMethod(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, null, "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/json", false, 0, true);
        task.execute(mockContext);
        assertNotNull(response);
        assertNotNull(response.get("content"));
    }

    @Test
    public void testGetRequestForResponseContent(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/json", false, 0, true);
        task.execute(mockContext);
        assertNotNull(response);
        assertNotNull(response.get("content"));
    }

    @Test
    public void testUnsuccessfulResponse(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/unsuccessful", false, 0, true);
        task.execute(mockContext);
        assertNotNull(response);
        assertFalse((Boolean) response.get("success"));
        assertTrue(((String) response.get("errorString")).length() > 0);
    }

    @Test
    public void testFilePostRequest(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "POST", "file", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/file", false, 0, true);
        when(mockContext.getVariable("body")).thenReturn("src/test/resources/__files/file.bin");
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
    }

    @Test
    public void testForMissingWorkDirForFileGetRequest(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            // Working directory is mandatory for response type file
            initCxtForRequest(mockContext, "GET", "json", "file",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/stringFile", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testFileGetRequestWithWorkDir(WireMockRuntimeInfo wmRuntimeInfo, @TempDir Path tempDir) throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "file",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/stringFile", false, 0, true);
        when(mockContext.getVariable("workDir")).thenReturn(tempDir.toString());
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
        assertEquals("stringFile", (new File((String) response.get("content"))).getName());
    }

    @Test
    public void testFileGetRequestWithNoFilename(WireMockRuntimeInfo wmRuntimeInfo, @TempDir Path tempDir) throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "file",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/fileUrlWithoutName/", false, 0, true);
        when(mockContext.getVariable("workDir")).thenReturn(tempDir.toString());
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
        assertTrue(((String) response.get("content")).matches(".*/tmpfile_.*\\.tmp$"));
    }

    @Test
    public void testFileGetWithResponseTypeString(WireMockRuntimeInfo wmRuntimeInfo, @TempDir Path tempDir) throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "string",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/stringFile", false, 0, true);
        when(mockContext.getVariable("workDir")).thenReturn(tempDir.toString());
        task.execute(mockContext);
        assertNotNull(response);
    }

    @Test
    public void testFileGetWithResponseTypeJSON(WireMockRuntimeInfo wmRuntimeInfo, @TempDir Path tempDir) throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/JSONFile", false, 0, true);
        when(mockContext.getVariable("workDir")).thenReturn(tempDir.toString());
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
    }

    @Test
    public void testPostJsonRequestForIncompatibleBody(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "POST", "json", "string",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/post", false, 0, true);
            when(mockContext.getVariable("body")).thenReturn("src/test/resources/__files/file.bin");
            task.execute(mockContext);
        });
    }

    @Test
    public void testPostStringRequestForIncompatibleComplexBody(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "POST", "string", "string",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/post", false, 0, true);
            when(mockContext.getVariable("body")).thenReturn(new HashMap<>());
            task.execute(mockContext);
        });
    }

    @Test
    public void testPostFileRequestForIncompatibleComplexBody(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "POST", "file", "string",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/post", false, 0, true);
            when(mockContext.getVariable("body")).thenReturn(new HashMap<>());
            task.execute(mockContext);
        });
    }

    @Test
    public void testInvalidRequestMethodType(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "GET1", "json", "file",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/file", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testInvalidRequestType(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "GET", "json1", "file",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/file", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testInvalidResponseType(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "GET", "json", "file1",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/file", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testOptionalResponseType(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "GET", "string", null, "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/string", false, 0, true);
        task.execute(mockContext);
        assertTrue((Boolean) response.get("success"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDelete(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "DELETE", "string", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/delete", false, 0, true);
        task.execute(mockContext);
        verify(deleteRequestedFor(urlEqualTo("/delete")));
        assertNotNull(response);
        assertEquals("Success", ((Map<String, Object>) response.get("content")).get("message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPatch(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        initCxtForRequest(mockContext, "PATCH", "json", "json",
                "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/patch", false, 0, true);
        when(mockContext.getVariable("body")).thenReturn("{ \"request\": \"PatchTest\" }");
        task.execute(mockContext);
        verify(patchRequestedFor(urlEqualTo("/patch")));
        assertNotNull(response);
        assertEquals("Success", ((Map<String, Object>) response.get("content")).get("message"));
    }

    @Test
    public void testRequestTimeoutException(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(RequestTimeoutException.class, () -> {
            initCxtForRequest(mockContext, "GET", "string", "string",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/requestTimeout", false, 5000, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testInvalidJsonResponse(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(RuntimeException.class, () -> {
            initCxtForRequest(mockContext, "GET", "json", "json",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/invalid/json", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testInvalidRequestMethod(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, 123, "json", "json",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/json", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testInvalidRequest(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "GET", 123, "json",
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/json", false, 0, true);
            task.execute(mockContext);
        });
    }

    @Test
    public void testInvalidResponse(WireMockRuntimeInfo wmRuntimeInfo) {
        assertThrows(IllegalArgumentException.class, () -> {
            initCxtForRequest(mockContext, "GET", "json", 123,
                    "http://localhost:" + wmRuntimeInfo.getHttpPort() + "/json", false, 0, true);
            task.execute(mockContext);
        });
    }

}
