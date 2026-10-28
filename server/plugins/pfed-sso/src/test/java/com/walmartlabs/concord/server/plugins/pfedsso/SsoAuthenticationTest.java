package com.walmartlabs.concord.server.plugins.pfedsso;

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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import org.apache.shiro.authc.AuthenticationInfo;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SsoAuthenticationTest {
    @Test
    void localOnlyLoginNeedsNoLdapAndLegacyTokenShapeIsRetained() throws Exception {
        SsoConfiguration cfg = mock(SsoConfiguration.class);
        when(cfg.isEnabled()).thenReturn(true);
        when(cfg.getIdentityAuthority()).thenReturn("issuer");
        when(cfg.getProfileProviderId()).thenReturn("local");
        when(cfg.getAuthorizationSources()).thenReturn(List.of());
        UserDirectory local = mock(UserDirectory.class);
        when(local.id()).thenReturn("local");
        ProvisioningPolicy policy = new SsoProvisioningPolicyProvider(cfg).get();
        ProviderRegistry registry = new ProviderRegistry(Set.of(local), Set.of(), Set.of(policy), Set.of());
        IdentityService identities = mock(IdentityService.class);
        SubjectPrincipal principal = new SubjectPrincipal(UUID.randomUUID(), "bob", null, new AuthenticationContext("sso", null, null));
        when(identities.resolve(any(), any())).thenReturn(principal);
        SsoRealm realm = new SsoRealm(identities, registry, cfg, mock(AuditLog.class, RETURNS_DEEP_STUBS));
        SsoToken token = SsoToken.verified("Bob", "Bob", null, null, null, null, null, Set.of());
        assertEquals(Set.of(principal), realm.getAuthenticationInfo(token).getPrincipals().asSet());
        var captured = org.mockito.ArgumentCaptor.forClass(VerifiedIdentity.class);
        verify(identities).resolve(captured.capture(), any());
        assertTrue(captured.getValue().identityLookups().isEmpty());
        assertEquals(Set.of("username", "domain", "displayName", "mail", "userPrincipalName", "nameInNamespace", "groups"),
                Arrays.stream(ObjectStreamClass.lookup(SsoToken.class).getFields()).map(ObjectStreamField::getName).collect(java.util.stream.Collectors.toSet()));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(token);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            SsoToken restored = (SsoToken) in.readObject();
            assertNull(restored.verifiedSubject());
            assertNull(realm.getAuthenticationInfo(restored));
        }
    }

    @Test
    void signedUserinfoIsInputOnlyAndLdapIsRequestedIndependentlyOfProfile() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        AtomicInteger userinfoCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            byte[] body = ("{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/userinfo", exchange -> {
            userinfoCalls.incrementAndGet();
            byte[] body = "{\"sub\":\" Alice@Example.COM \",\"sAMAccountName\":\"Alice\",\"mail\":\"a@example.com\",\"memberOf\":[\"untrusted-group\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            SsoConfiguration cfg = mock(SsoConfiguration.class);
            when(cfg.isEnabled()).thenReturn(true);
            when(cfg.getEnableBearerTokens()).thenReturn(true);
            when(cfg.getAllowAllClientIds()).thenReturn(true);
            when(cfg.isTokenSignatureValidation()).thenReturn(true);
            when(cfg.getTokenSigningKeyUrl()).thenReturn(base + "/jwks");
            when(cfg.getUserInfoEndpointUrl()).thenReturn(base + "/userinfo");
            when(cfg.getIdentityAuthority()).thenReturn(base + "/userinfo");
            when(cfg.getTokenServiceConnectTimeout()).thenReturn(Duration.ofSeconds(5));
            when(cfg.getTokenServiceReadTimeout()).thenReturn(Duration.ofSeconds(5));
            when(cfg.getIdentityDirectory()).thenReturn("ldap");
            when(cfg.getProfileProviderId()).thenReturn("local");
            when(cfg.getAuthorizationSources()).thenReturn(List.of("ldap"));
            SsoClient client = new SsoClient(cfg);
            SsoHandler handler = new SsoHandler(cfg, new JwtAuthenticator(cfg, client), mock(RedirectHelper.class), client);
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            SignedJWT valid = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                    new JWTClaimsSet.Builder().subject("subject").expirationTime(new Date(System.currentTimeMillis() + 60000)).build());
            valid.sign(new RSASSASigner(key));
            SignedJWT invalid = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), valid.getJWTClaimsSet());
            invalid.sign(new RSASSASigner(new RSAKeyGenerator(2048).generate()));
            when(request.getHeader("Authorization")).thenReturn("Bearer " + invalid.serialize());
            assertNull(handler.createToken(request, response));
            when(request.getHeader("Authorization")).thenReturn("Bearer " + new PlainJWT(valid.getJWTClaimsSet()).serialize());
            assertNull(handler.createToken(request, response));
            assertEquals(0, userinfoCalls.get());
            when(request.getHeader("Authorization")).thenReturn("Bearer " + valid.serialize());
            SsoToken token = (SsoToken) handler.createToken(request, response);
            assertNotNull(token);
            assertEquals(1, userinfoCalls.get());

            UserDirectory local = mock(UserDirectory.class);
            when(local.id()).thenReturn("local");
            UserDirectory ldap = mock(UserDirectory.class);
            when(ldap.id()).thenReturn("ldap");
            AuthorizationProvider source = mock(AuthorizationProvider.class);
            when(source.id()).thenReturn("ldap");
            ProvisioningPolicy policy = new SsoProvisioningPolicyProvider(cfg).get();
            ProviderRegistry registry = new ProviderRegistry(Set.of(local, ldap), Set.of(source), Set.of(policy), Set.of(new SsoLegacyIdentityMatcher()));
            IdentityService identities = mock(IdentityService.class);
            SubjectPrincipal principal = new SubjectPrincipal(UUID.randomUUID(), "alice", "example.com", new AuthenticationContext("sso", null, null));
            when(identities.resolve(any(), any())).thenReturn(principal);
            SsoRealm realm = new SsoRealm(identities, registry, cfg, mock(AuditLog.class, RETURNS_DEEP_STUBS));
            AuthenticationInfo info = realm.getAuthenticationInfo(token);
            assertEquals(Set.of(principal), info.getPrincipals().asSet());
            var identity = org.mockito.ArgumentCaptor.forClass(VerifiedIdentity.class);
            verify(identities).resolve(identity.capture(), any());
            VerifiedIdentity verified = identity.getValue();
            assertEquals(" Alice@Example.COM ", verified.identity().subject());
            assertEquals(base + "/userinfo", verified.identity().authority());
            assertTrue(verified.authorizationAttributes().isEmpty());
            assertEquals(Set.of(new IdentityLookup("ldap", new DirectoryQuery("username", "Alice", "Example.COM"), Set.of("ldap"), false)), verified.identityLookups());
            assertEquals(new LegacyAccountSelector("alice", "example.com", "LDAP"), new SsoLegacyIdentityMatcher().selector(verified).orElseThrow());
            assertNull(realm.getAuthenticationInfo(new SsoToken("Alice", "example.com", null, null, null, null, Set.of())));
        } finally {
            server.stop(0);
        }
    }
}
