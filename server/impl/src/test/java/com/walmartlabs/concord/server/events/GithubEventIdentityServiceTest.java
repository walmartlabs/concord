package com.walmartlabs.concord.server.events;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.org.project.EncryptedProjectValueManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.identity.ExternalEventContext;
import com.walmartlabs.concord.server.sdk.identity.ExternalEventIdentityResolver;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityResolution;
import com.walmartlabs.concord.server.security.GithubAuthenticatingFilter;
import com.walmartlabs.concord.server.security.github.GithubKey;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubEventIdentityServiceTest {

    private static final String AUTHORITY = "https://trusted.example.com";
    private static final String SECRET = "test-webhook-secret";
    private static final String PAYLOAD = "{ \"repository\": {\"full_name\":\"org/repo\"}, \"sender\": {\"url\":\"https://untrusted.example.com/bot\", \"type\":\"Bot\"} }";
    private static final ExternalIdentityRef IDENTITY = new ExternalIdentityRef("registrar", AUTHORITY, "123");

    @Test
    void noPluginRequiresNeitherAuthorityNorAuthenticatedContext() {
        new GithubEventIdentityService(mock(GithubConfiguration.class), Set.of())
                .assertInitiatorAllowed(null, null, null);
    }

    @Test
    void installedResolverRequiresExplicitValidAuthority() {
        for (var authority : new String[]{null, "", " ", "a".repeat(513)}) {
            var cfg = mock(GithubConfiguration.class);
            when(cfg.getExternalIdentityAuthority()).thenReturn(authority);
            assertThrows(IllegalArgumentException.class, () -> new GithubEventIdentityService(cfg,
                    Set.of(context -> new ExternalIdentityResolution.Unhandled())));
        }
    }

    @Test
    void signedPayloadUsesConfiguredAuthorityAndOriginalBody() throws Exception {
        var request = authenticate(signature(), false, true);
        var calls = new AtomicInteger();
        ExternalEventIdentityResolver resolver = context -> {
            assertEquals("github", context.source());
            assertEquals("push", context.eventType());
            assertEquals("delivery-123", context.deliveryId());
            assertEquals(AUTHORITY, context.authority());
            assertEquals(PAYLOAD, context.payload());
            assertEquals(ExternalEventContext.Authentication.SIGNED_PAYLOAD, context.authentication());
            calls.incrementAndGet();
            return new ExternalIdentityResolution.Unhandled();
        };
        service(resolver).assertInitiatorAllowed(request, "push", "delivery-123");
        assertEquals(1, calls.get());
    }

    @Test
    void repositoryCredentialDoesNotBecomeSignedPayloadFromHeaderPresence() throws Exception {
        var request = authenticate("sha1=not-a-valid-signature", true, true);
        service(context -> {
            assertEquals(ExternalEventContext.Authentication.ENDPOINT_CREDENTIAL, context.authentication());
            assertEquals(PAYLOAD, context.payload());
            return new ExternalIdentityResolution.Unhandled();
        }).assertInitiatorAllowed(request, "push", null);
    }

    @Test
    void invalidCredentialsNeverExposeAuthenticatedContext() throws Exception {
        for (var signature : new String[]{null, "sha1=invalid", "sha256=invalid"}) {
            var request = authenticate(signature, false, false);
            assertThrows(IllegalStateException.class, () -> service(context -> {
                fail("Resolver must not see an unauthenticated body");
                return new ExternalIdentityResolution.Unhandled();
            }).assertInitiatorAllowed(request, "push", null));
        }
        var request = authenticate(signature(), true, false);
        assertThrows(IllegalStateException.class, () -> GithubAuthenticatingFilter.getEventContext(request, AUTHORITY, "push", null));
    }

    @Test
    void recognizedExternalActorRemainsBlocked() throws Exception {
        var request = authenticate(signature(), false, true);
        var error = assertThrows(ConcordApplicationException.class, () -> service(context ->
                new ExternalIdentityResolution.Resolved(IDENTITY)).assertInitiatorAllowed(request, "push", null));
        assertTrue(error.getMessage().contains("execution is disabled"));
    }

    @Test
    void rejectedNullAndFailedLookupsDoNotFallThrough() throws Exception {
        var request = authenticate(signature(), false, true);
        assertThrows(ConcordApplicationException.class, () -> service(context ->
                new ExternalIdentityResolution.Rejected("Unverified actor")).assertInitiatorAllowed(request, "push", null));
        assertThrows(ConcordApplicationException.class, () -> service(context -> null).assertInitiatorAllowed(request, "push", null));
        var failure = new ExternalIdentityException("Registrar unavailable");
        var error = assertThrows(ConcordApplicationException.class, () -> service(context -> {
            throw failure;
        }).assertInitiatorAllowed(request, "push", null));
        assertSame(failure, error.getCause());
        var unchecked = new IllegalStateException("Plugin failure");
        assertSame(unchecked, assertThrows(IllegalStateException.class, () -> service(context -> {
            throw unchecked;
        }).assertInitiatorAllowed(request, "push", null)));
    }

    @Test
    void allResolversMustLeaveActorUnhandledToPermitUserResolution() throws Exception {
        var request = authenticate(signature(), false, true);
        var first = mock(ExternalEventIdentityResolver.class);
        var second = mock(ExternalEventIdentityResolver.class);
        when(first.resolve(any())).thenReturn(new ExternalIdentityResolution.Unhandled());
        when(second.resolve(any())).thenReturn(new ExternalIdentityResolution.Unhandled());
        new GithubEventIdentityService(configuration(), Set.of(first, second)).assertInitiatorAllowed(request, "push", null);
        verify(first).resolve(any());
        verify(second).resolve(any());
    }

    @Test
    void authorityMismatchIsNotAClaimForAnotherInstance() throws Exception {
        var request = authenticate(signature(), false, true);
        var error = assertThrows(ConcordApplicationException.class, () -> service(context ->
                new ExternalIdentityResolution.Resolved(new ExternalIdentityRef("registrar", "other", "123")))
                .assertInitiatorAllowed(request, "push", null));
        assertTrue(error.getMessage().contains("different authority"));
    }

    @Test
    void multipleClaimsFailInEitherRegistrationOrderEvenForSameIdentity() throws Exception {
        var request = authenticate(signature(), false, true);
        ExternalEventIdentityResolver first = context -> new ExternalIdentityResolution.Resolved(IDENTITY);
        ExternalEventIdentityResolver second = context -> new ExternalIdentityResolution.Resolved(IDENTITY);
        for (var order : Set.of(java.util.List.of(first, second), java.util.List.of(second, first))) {
            var cfg = configuration();
            var service = new GithubEventIdentityService(cfg, new LinkedHashSet<>(order));
            var error = assertThrows(ConcordApplicationException.class, () -> service.assertInitiatorAllowed(request, "push", null));
            assertTrue(error.getMessage().contains("Multiple external event identity resolvers"));
        }
    }

    private GithubEventIdentityService service(ExternalEventIdentityResolver resolver) {
        return new GithubEventIdentityService(configuration(), Set.of(resolver));
    }

    private GithubConfiguration configuration() {
        var cfg = mock(GithubConfiguration.class);
        when(cfg.getExternalIdentityAuthority()).thenReturn(AUTHORITY);
        return cfg;
    }

    private String signature() throws Exception {
        var mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return "sha1=" + HexFormat.of().formatHex(mac.doFinal(PAYLOAD.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpServletRequest authenticate(String signature, boolean repositoryCredential, boolean accepted) throws Exception {
        var cfg = mock(GithubConfiguration.class);
        when(cfg.getSecret()).thenReturn(SECRET);
        var encryptedValues = mock(EncryptedProjectValueManager.class);
        var filter = new GithubAuthenticatingFilter(cfg, encryptedValues, new ObjectMapper()) {
            @Override
            protected boolean preHandle(ServletRequest request, ServletResponse response) throws Exception {
                var token = createToken(request, response);
                assertEquals(accepted, token instanceof GithubKey);
                return token instanceof GithubKey;
            }
        };
        var request = mock(HttpServletRequest.class);
        var attributes = new HashMap<String, Object>();
        when(request.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(request).setAttribute(anyString(), any());
        doAnswer(invocation -> attributes.remove(invocation.getArgument(0)))
                .when(request).removeAttribute(anyString());
        when(request.getHeader("X-Hub-Signature")).thenReturn(signature);
        if (repositoryCredential) {
            var projectId = UUID.randomUUID();
            var token = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
            when(request.getParameter("hookProjectId")).thenReturn(projectId.toString());
            when(request.getParameter("hookRepoToken")).thenReturn(token);
            var hash = Base64.getEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest("repo".getBytes(StandardCharsets.UTF_8)));
            when(encryptedValues.decrypt(eq(projectId), any())).thenReturn(
                    (accepted ? hash : "invalid").getBytes(StandardCharsets.UTF_8));
        }
        var bytes = new ByteArrayInputStream(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override
            public int read() {
                return bytes.read();
            }

            @Override
            public boolean isFinished() {
                return bytes.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException();
            }
        });
        filter.doFilterInternal(request, mock(HttpServletResponse.class), (req, resp) -> {});
        return request;
    }
}
