package com.walmartlabs.concord.runner;

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

import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.client2.ConcordApiClient;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecretServiceImplTest {

    private static final UUID INSTANCE_ID = UUID.fromString("a6a631c6-48e9-41ab-88f8-9a081f9acafb");
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private HttpClient httpClient;
    private SecretServiceImpl service;
    private Context context;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/process/" + INSTANCE_ID + "/decrypt", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                // Echo the decoded bytes to verify what the service sends to the API.
                var body = exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        httpClient = HttpClient.newHttpClient();
        var apiClient = new ConcordApiClient(httpClient).setScheme("http").setHost("127.0.0.1")
                .setPort(server.getAddress().getPort()).setBasePath("");
        context = mock(Context.class);
        when(context.getVariable(Constants.Request.PROCESS_INFO_KEY)).thenReturn(Map.of("sessionKey", "test-session"));
        service = new SecretServiceImpl(cfg -> apiClient);
    }

    @AfterEach
    void tearDown() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void decodesStandardBase64() throws Exception {
        for (var entry : Map.of("YQ==", "a", "YWI=", "ab", "YWJj", "abc").entrySet()) {
            var input = entry.getKey();
            assertEquals(entry.getValue(), service.decryptString(context, INSTANCE_ID.toString(), input));
        }
        assertEquals(3, requests.get());
    }

    @Test
    void acceptsWhitespaceIncludingBetweenPaddingCharacters() throws Exception {
        for (var input : new String[]{" Y Q = = ", "YQ=\n=", "YQ=\r\n=", "YQ=\t=", "\nYQ==\r\n"}) {
            assertEquals("a", service.decryptString(context, INSTANCE_ID.toString(), input), input);
        }
        assertEquals(5, requests.get());
    }

    @Test
    void rejectsMalformedPaddingBeforeCallingTheApi() {
        for (var input : new String[]{"Y=Q=", "YQ==YQ==", "="}) {
            var error = assertThrows(IllegalArgumentException.class, () -> service.decryptString(context, INSTANCE_ID.toString(), input));
            assertTrue(error.getMessage().startsWith("Invalid encrypted string value"));
        }
        assertEquals(0, requests.get());
    }
}
