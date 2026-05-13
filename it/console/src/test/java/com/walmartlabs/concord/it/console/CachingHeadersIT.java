package com.walmartlabs.concord.it.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static com.walmartlabs.concord.it.console.Utils.env;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class CachingHeadersIT {

    private static final String BASE_URL = env("IT_SERVER_BASE_URL", "http://localhost:8001");
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=2592000, immutable";
    private static final Pattern STATIC_RESOURCE_PATTERN = Pattern.compile("(?:src|href)=\"([^\"]+\\.(?:js|css))\"");

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    public void webappResourcesUseCachingHeaders() throws Exception {
        var index = get("/");

        assumeTrue(index.headers().firstValue("ETag").isPresent(),
                "packaged WebappFilter headers are not available for this IT_SERVER_BASE_URL");

        assertEquals(200, index.statusCode());
        assertIndexResource(index);
        assertNotModified("/", header(index, "ETag"), null);

        var indexHtml = get("/index.html");

        assertEquals(200, indexHtml.statusCode());
        assertEquals(header(index, "ETag"), header(indexHtml, "ETag"));
        assertIndexResource(indexHtml);
        assertNotModified("/index.html", header(indexHtml, "ETag"), null);

        var resourcePath = findStaticResource(index.body());
        var resource = get(resourcePath);

        assertEquals(200, resource.statusCode());
        assertImmutableResource(resource);
        assertNotModified(resourcePath, header(resource, "ETag"));
    }

    @Test
    public void webappFallbackRoutesDoNotUseImmutableCachePolicy() throws Exception {
        var resp = get("/missing/spa/route");

        assumeTrue(resp.headers().firstValue("ETag").isPresent(),
                "packaged WebappFilter headers are not available for this IT_SERVER_BASE_URL");

        assertEquals(200, resp.statusCode());
        assertFalse(resp.headers().firstValue("Cache-Control")
                .filter(IMMUTABLE_CACHE_CONTROL::equals)
                .isPresent());
        assertNotModified("/missing/spa/route", header(resp, "ETag"), null);
    }

    @Test
    public void cfgJsUsesNoCachePolicy() throws Exception {
        var resp = get("/cfg.js");

        assertEquals(200, resp.statusCode());
        assertNoCache(resp);
        assertNotModified("/cfg.js", header(resp, "ETag"), "no-cache, no-store, must-revalidate");
    }

    @Test
    public void apiResponsesUseNoCachePolicy() throws Exception {
        var resp = get("/api/v1/server/ping");

        assertEquals(200, resp.statusCode());
        assertNoCache(resp);
    }

    private void assertImmutableResource(HttpResponse<String> resp) {
        assertEquals(IMMUTABLE_CACHE_CONTROL, header(resp, "Cache-Control"));
        assertTrue(header(resp, "ETag").matches("\".+\""));
    }

    private static void assertIndexResource(HttpResponse<String> resp) {
        assertFalse(resp.headers().firstValue("Cache-Control")
                .filter(IMMUTABLE_CACHE_CONTROL::equals)
                .isPresent());
        assertTrue(header(resp, "ETag").matches("\".+\""));
    }

    private void assertNotModified(String path, String eTag) throws Exception {
        assertNotModified(path, eTag, IMMUTABLE_CACHE_CONTROL);
    }

    private void assertNotModified(String path, String eTag, String expectedCacheControl) throws Exception {
        var resp = get(path, "If-None-Match", eTag);

        assertEquals(304, resp.statusCode());
        assertEquals(eTag, header(resp, "ETag"));
        if (expectedCacheControl != null) {
            assertEquals(expectedCacheControl, header(resp, "Cache-Control"));
        } else {
            assertFalse(resp.headers().firstValue("Cache-Control").isPresent());
        }
        assertTrue(resp.body().isEmpty());
    }

    private static void assertNoCache(HttpResponse<String> resp) {
        assertEquals("no-cache, no-store, must-revalidate", header(resp, "Cache-Control"));
        assertEquals("no-cache", header(resp, "Pragma"));
        assertEquals("0", header(resp, "Expires"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null, null);
    }

    private HttpResponse<String> get(String path, String headerName, String headerValue) throws Exception {
        var builder = HttpRequest.newBuilder()
                .GET()
                .uri(resolve(path));

        if (headerName != null) {
            builder.header(headerName, headerValue);
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI resolve(String path) {
        var uri = URI.create(path);
        if (uri.isAbsolute()) {
            return uri;
        }

        return URI.create(BASE_URL).resolve(path);
    }

    private static String header(HttpResponse<?> resp, String name) {
        return resp.headers()
                .firstValue(name)
                .orElseThrow(() -> new AssertionError("Missing %s header in %s response".formatted(name, resp.uri())));
    }

    private static String findStaticResource(String html) {
        var matcher = STATIC_RESOURCE_PATTERN.matcher(html);
        while (matcher.find()) {
            var path = matcher.group(1);
            if (!path.startsWith("/api/")) {
                return path;
            }
        }

        assertFalse(html.isEmpty(), "Expected console HTML to contain static resource links");
        throw new AssertionError("No static JS or CSS resource link found in console HTML");
    }
}
