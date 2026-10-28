package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OidcFlowTest {

    @Test
    void userinfoObservationAndTokenExpiryAreBounded() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var base = "http://127.0.0.1:" + server.getAddress().getPort();
        var tokenResponse = new AtomicReference<>("{\"access_token\":\"secret-token\",\"expires_in\":120}");
        server.createContext("/token", exchange -> reply(exchange, tokenResponse.get()));
        server.createContext("/discovery", exchange -> reply(exchange, "{\"issuer\":\"issuer\",\"authorization_endpoint\":\"" + base + "/auth\",\"token_endpoint\":\"" + base + "/token\",\"userinfo_endpoint\":\"" + base + "/userinfo\"}"));
        server.createContext("/userinfo", exchange -> reply(exchange, "{\"sub\":\"user\",\"email\":\"u@example.com\",\"name\":\"User\",\"groups\":[\"users\"],\"access_token\":\"secret-token\",\"nested\":{\"refresh_token\":\"refresh-secret\"}}"));
        server.start();
        try {
            var cfg = mock(PluginConfiguration.class);
            when(cfg.getDiscoveryUri()).thenReturn(base + "/discovery");
            when(cfg.getClientId()).thenReturn("client");
            when(cfg.getSecret()).thenReturn("secret");
            var service = new OidcService(cfg);
            var before = Instant.now();
            var callback = service.exchangeCodeForProfile("code", "http://localhost/callback");
            assertEquals("issuer", callback.issuer());
            assertFalse(callback.observedAt().isBefore(before));
            assertTrue(callback.expiresAt().isAfter(callback.observedAt()));
            assertFalse(callback.expiresAt().isAfter(Instant.now().plusSeconds(120)));
            assertNull(callback.profile().accessToken());
            assertFalse(callback.profile().attributes().containsKey("access_token"));
            assertFalse(((Map<?, ?>) callback.profile().attributes().get("nested")).containsKey("refresh_token"));
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) {
                out.writeObject(new OidcToken(callback));
            }
            var serialized = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
            assertFalse(serialized.contains("secret-token"));
            assertFalse(serialized.contains("refresh-secret"));
            var bearer = service.validateToken("secret-token");
            assertEquals(Duration.ofHours(1), Duration.between(bearer.observedAt(), bearer.expiresAt()));
            tokenResponse.set("{\"access_token\":\"secret-token\",\"expires_in\":0}");
            assertThrows(IOException.class, () -> service.exchangeCodeForProfile("code", "http://localhost/callback"));
            tokenResponse.set("{\"access_token\":\"secret-token\"}");
            var withoutExpiry = service.exchangeCodeForProfile("code", "http://localhost/callback");
            assertEquals(Duration.ofHours(1), Duration.between(withoutExpiry.observedAt(), withoutExpiry.expiresAt()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingIssuerIsRejected() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/discovery", exchange -> reply(exchange, "{}"));
        server.start();
        try {
            var cfg = mock(PluginConfiguration.class);
            when(cfg.getDiscoveryUri()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + "/discovery");
            assertThrows(IllegalStateException.class, () -> new OidcService(cfg).validateToken("token"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void expiredAndLegacyCachedProfilesAreCleared() {
        var cfg = mock(PluginConfiguration.class);
        when(cfg.isEnabled()).thenReturn(true);
        var handler = new OidcAuthenticationHandler(cfg, mock(OidcService.class));
        var request = mock(HttpServletRequest.class);
        var session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        var now = Instant.now();
        var profile = new UserProfile("id", "email", "name", "token", Map.of("groups", List.of("users")));
        var valid = new VerifiedProfile(profile, "issuer", now, now.plusSeconds(60));
        when(session.getAttribute("OIDC_USER_PROFILE")).thenReturn(
                new VerifiedProfile(profile, "issuer", now.minusSeconds(60), now.minusSeconds(1)), profile, valid);
        assertNull(handler.createToken(request, mock(HttpServletResponse.class)));
        assertNull(handler.createToken(request, mock(HttpServletResponse.class)));
        assertSame(valid, ((OidcToken) handler.createToken(request, mock(HttpServletResponse.class))).getVerifiedProfile());
        verify(session, times(2)).removeAttribute("OIDC_USER_PROFILE");
    }

    @Test
    void callbackAuthenticatesEvenForAuthenticatedSubject() throws Exception {
        var cfg = mock(PluginConfiguration.class);
        when(cfg.isEnabled()).thenReturn(true);
        when(cfg.getUrlBase()).thenReturn("http://localhost");
        when(cfg.getAfterLoginUrl()).thenReturn("/");
        var service = mock(OidcService.class);
        var now = Instant.now();
        var profile = new VerifiedProfile(new UserProfile("id", "email", "name", "token", Map.of()), "issuer", now, now.plusSeconds(60));
        when(service.exchangeCodeForProfile(any(), any())).thenReturn(profile);
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("code")).thenReturn("code");
        when(request.getParameter("state")).thenReturn("state");
        when(session.getAttribute("OIDC_STATE")).thenReturn("state");
        when(response.encodeRedirectURL(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var subject = mock(Subject.class);
        when(subject.isAuthenticated()).thenReturn(true);
        ThreadContext.bind(subject);
        try {
            new OidcCallbackFilter(cfg, service).doFilter(request, response, mock(FilterChain.class));
            verify(subject).login(any(OidcToken.class));
            verify(session).setAttribute("OIDC_USER_PROFILE", profile);
        } finally {
            ThreadContext.unbindSubject();
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
