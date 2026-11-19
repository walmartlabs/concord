package com.walmartlabs.concord.server.boot;

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

import com.walmartlabs.concord.server.cfg.ServerConfiguration;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pins container behavior that the server relies on but the compiler can't check. */
class HttpServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;

    @AfterEach
    void stop() throws Exception {
        client.close();
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void sessionCookieUsesSameSiteFromTheDefaultCookieComment() throws Exception {
        URI base = start("__SAME_SITE_LAX__", false);

        String cookie = get(base.resolve("/test/session")).headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.startsWith("JSESSIONID="), cookie);
        assertTrue(cookie.contains("; HttpOnly"), cookie);
        assertTrue(cookie.contains("; SameSite=Lax"), cookie);
        assertFalse(cookie.contains("; Secure"), cookie);
    }

    @Test
    void sessionCookieIsSecureWhenConfigured() throws Exception {
        URI base = start("__SAME_SITE_STRICT__", true);

        String cookie = get(base.resolve("/test/session")).headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.contains("; SameSite=Strict"), cookie);
        assertTrue(cookie.contains("; Secure"), cookie);
    }

    @Test
    void acceptsEncodedSlashInPath() throws Exception {
        URI base = start(null, false);

        HttpResponse<String> response = get(URI.create(base + "/test/path/a%2Fb"));
        assertEquals(200, response.statusCode());
        assertEquals("/path/a/b", response.body());
    }

    private URI start(String cookieComment, boolean secureCookies) throws Exception {
        ServerConfiguration cfg = mock(ServerConfiguration.class);
        when(cfg.getPort()).thenReturn(0);
        when(cfg.getSessionTimeout()).thenReturn(Duration.ofMinutes(5));
        when(cfg.getRequestHeaderSize()).thenReturn(8192);
        when(cfg.getCookieComment()).thenReturn(cookieComment);
        when(cfg.isSecureCookies()).thenReturn(secureCookies);

        server = new HttpServer(cfg, Set.of(), Set.of(), Set.of(), Set.of(new TestServlet()),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        server.start();

        ServerConnector connector = (ServerConnector) server.getStatisticsHandler().getServer().getConnectors()[0];
        return URI.create("http://localhost:" + connector.getLocalPort());
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
    }

    @WebServlet("/test/*")
    public static class TestServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if ("/session".equals(req.getPathInfo())) {
                req.getSession(true);
            }
            resp.getWriter().write(req.getPathInfo());
        }
    }
}
