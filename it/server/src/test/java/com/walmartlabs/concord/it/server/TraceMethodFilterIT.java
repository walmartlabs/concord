package com.walmartlabs.concord.it.server;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TraceMethodFilterIT extends AbstractServerIT {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    public void testTraceWebSocket() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(ITConstants.SERVER_URL + "/websocket"))
                .method("TRACE", HttpRequest.BodyPublishers.noBody())
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.discarding());
        assertEquals(405, resp.statusCode());
    }

    @Test
    public void testTraceRoot() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(ITConstants.SERVER_URL + "/"))
                .method("TRACE", HttpRequest.BodyPublishers.noBody())
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.discarding());
        assertEquals(405, resp.statusCode());
    }

    @Test
    public void testTraceApi() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(ITConstants.SERVER_URL + "/api/v1/server/version"))
                .method("TRACE", HttpRequest.BodyPublishers.noBody())
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.discarding());
        assertEquals(405, resp.statusCode());
    }

    @Test
    public void testGetUnaffected() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(ITConstants.SERVER_URL + "/api/v1/server/version"))
                .GET()
                .build();

        var resp = client.send(req, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, resp.statusCode());
    }
}
