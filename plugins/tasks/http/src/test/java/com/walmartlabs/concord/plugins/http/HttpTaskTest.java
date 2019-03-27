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

import com.walmartlabs.concord.sdk.Context;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpTaskTest extends AbstractHttpTaskTest {

    private Context mockContext = mock(Context.class);

    @Test
    public void testForAsStringMethod() throws Exception {
        String response = task.asString("http://localhost:" + rule.port() + "/json");
        verify(getRequestedFor(urlEqualTo("/json")));
        assertNotNull(response);
    }

    @Test
    public void testExecuteGetRequestForJson() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json", "http://localhost:" + rule.port() + "/json", false);
        task.execute(mockContext);
        verify(getRequestedFor(urlEqualTo("/json")));
        assertEquals(200, response.get("statusCode"));
        assertNull(response.get("errorString"));
    }

    @Test
    public void testExecuteGetRequestForString() throws Exception {
        initCxtForRequest(mockContext, "GET", "string", "string", "http://localhost:" + rule.port() + "/string", false);
        task.execute(mockContext);
        verify(getRequestedFor(urlEqualTo("/string")));
    }

    @Test
    public void testExecutePostRequestForJson() throws Exception {
        initCxtForRequest(mockContext, "POST", "json", "json", "http://localhost:" + rule.port() + "/post", false);
        when(mockContext.getVariable("body")).thenReturn("{ \"request\": \"PostTest\" }");
        task.execute(mockContext);
        verify(postRequestedFor(urlEqualTo("/post"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    public void testExecutePostRequestForComplexObject() throws Exception {
        initCxtForRequest(mockContext, "POST", "json", "json", "http://localhost:" + rule.port() + "/post", false);
        HashMap<String, Object> complexObject = new HashMap<>();
        HashMap<String, Object> nestedObject = new HashMap<>();
        nestedObject.put("nestedVar", 123);
        complexObject.put("myObject", nestedObject);
        when(mockContext.getVariable("body")).thenReturn(complexObject);
        task.execute(mockContext);
        verify(postRequestedFor(urlEqualTo("/post"))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test(expected = Exception.class)
    public void testExecuteForException() throws Exception {
        initCxtForRequest(mockContext, "GET", "string", "string", "http://localhost:" + rule.port() + "/fault", false);
        task.execute(mockContext);
    }

    @Test
    public void testExecuteWithIgnoreError() throws Exception {
        initCxtForRequest(mockContext, "GET", "string", "string", "http://localhost:" + rule.port() + "/fault", true);
        task.execute(mockContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentExceptionForRequest() throws Exception {
        task.execute(mockContext);
    }

    @Test
    public void testGetRequestForResponseContent() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json", "http://localhost:" + rule.port() + "/json", false);
        task.execute(mockContext);
        assertNotNull(response);
        assertNotNull(response.get("content"));
    }

    @Test
    public void testUnsuccessfulResponse() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json", "http://localhost:" + rule.port() + "/unsuccessful", false);
        task.execute(mockContext);
        assertNotNull(response);
        assertFalse((Boolean) response.get("success"));
        assertTrue(((String) response.get("errorString")).length() > 0);
    }

    @Test
    public void testFilePostRequest() throws Exception {
        initCxtForRequest(mockContext, "POST", "file", "json", "http://localhost:" + rule.port() + "/file", false);
        when(mockContext.getVariable("body")).thenReturn("src/test/resources/__files/file.bin");
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForMissingWorkDirForFileGetRequest() throws Exception {
        // Working directory is mandatory for response type file
        initCxtForRequest(mockContext, "GET", "json", "file", "http://localhost:" + rule.port() + "/stringFile", false);
        task.execute(mockContext);

    }

    @Test
    public void testFileGetRequestWithWorkDir() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "file", "http://localhost:" + rule.port() + "/stringFile", false);
        when(mockContext.getVariable("workDir")).thenReturn(folder.getRoot().toString());
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
        assertEquals("stringFile", (new File((String) response.get("content"))).getName());
    }

    @Test
    public void testFileGetRequestWithNoFilename() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "file", "http://localhost:" + rule.port() + "/fileUrlWithoutName/", false);
        when(mockContext.getVariable("workDir")).thenReturn(folder.getRoot().toString());
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
        assertTrue(((String) response.get("content")).matches(".*/tmpfile_.*\\.tmp$"));
    }

    @Test
    public void testFileGetWithResponseTypeString() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "string", "http://localhost:" + rule.port() + "/stringFile", false);
        when(mockContext.getVariable("workDir")).thenReturn(folder.getRoot().toString());
        task.execute(mockContext);
        assertNotNull(response);
    }

    @Test
    public void testFileGetWithResponseTypeJSON() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "json", "http://localhost:" + rule.port() + "/JSONFile", false);
        when(mockContext.getVariable("workDir")).thenReturn(folder.getRoot().toString());
        task.execute(mockContext);
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPostJsonRequestForIncompatibleBody() throws Exception {
        initCxtForRequest(mockContext, "POST", "json", "string", "http://localhost:" + rule.port() + "/post", false);
        when(mockContext.getVariable("body")).thenReturn("src/test/resources/__files/file.bin");
        task.execute(mockContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPostStringRequestForIncompatibleComplexBody() throws Exception {
        initCxtForRequest(mockContext, "POST", "string", "string", "http://localhost:" + rule.port() + "/post", false);
        when(mockContext.getVariable("body")).thenReturn(new HashMap<>());
        task.execute(mockContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPostFileRequestForIncompatibleComplexBody() throws Exception {
        initCxtForRequest(mockContext, "POST", "file", "string", "http://localhost:" + rule.port() + "/post", false);
        when(mockContext.getVariable("body")).thenReturn(new HashMap<>());
        task.execute(mockContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRequestMethodType() throws Exception {
        initCxtForRequest(mockContext, "GET1", "json", "file", "http://localhost:" + rule.port() + "/file", false);
        task.execute(mockContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRequestType() throws Exception {
        initCxtForRequest(mockContext, "GET", "json1", "file", "http://localhost:" + rule.port() + "/file", false);
        task.execute(mockContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidResponseType() throws Exception {
        initCxtForRequest(mockContext, "GET", "json", "file1", "http://localhost:" + rule.port() + "/file", false);
        task.execute(mockContext);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDelete() throws Exception {
        initCxtForRequest(mockContext, "DELETE", "string", "json", "http://localhost:" + rule.port() + "/delete", false);
        task.execute(mockContext);
        verify(deleteRequestedFor(urlEqualTo("/delete")));
        assertNotNull(response);
        assertEquals("Success", ((Map<String, Object>) response.get("content")).get("message"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPatch() throws Exception {
        initCxtForRequest(mockContext, "PATCH", "json", "json", "http://localhost:" + rule.port() + "/patch", false);
        when(mockContext.getVariable("body")).thenReturn("{ \"request\": \"PatchTest\" }");
        task.execute(mockContext);
        verify(patchRequestedFor(urlEqualTo("/patch")));
        assertNotNull(response);
        assertEquals("Success", ((Map<String, Object>) response.get("content")).get("message"));
    }

}
