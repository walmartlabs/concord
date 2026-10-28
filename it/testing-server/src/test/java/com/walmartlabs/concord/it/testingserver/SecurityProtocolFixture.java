package com.walmartlabs.concord.it.testingserver;

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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Local OAuth protocol peer with an actual RSA key and signature-checked userinfo. */
final class SecurityProtocolFixture implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final HttpServer server;
    private final KeyPair key;
    private volatile String codeSubject = "alice";
    private volatile String email;

    SecurityProtocolFixture() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        key = generator.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", this::jwks);
        server.createContext("/discovery", this::discovery);
        server.createContext("/.well-known/openid-configuration", this::discovery);
        server.createContext("/token", this::token);
        server.createContext("/userinfo", this::userinfo);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void codeSubject(String subject) {
        codeSubject = subject;
    }

    void email(String email) {
        this.email = email;
    }

    String signedToken(String subject) throws Exception {
        String header = encode(JSON.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT", "kid", "test-key")));
        String claims = encode(JSON.writeValueAsBytes(Map.of("sub", subject, "client_id", "test-client", "iss", baseUrl(), "exp", Instant.now().plusSeconds(3600).getEpochSecond())));
        String payload = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.US_ASCII));
        return payload + "." + encode(signature.sign());
    }

    private void jwks(HttpExchange exchange) throws IOException {
        RSAPublicKey publicKey = (RSAPublicKey) key.getPublic();
        respond(exchange, 200, Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "test-key", "alg", "RS256", "use", "sig", "n", unsigned(publicKey.getModulus()), "e", unsigned(publicKey.getPublicExponent())))));
    }

    private void discovery(HttpExchange exchange) throws IOException {
        respond(exchange, 200, Map.of("issuer", baseUrl(), "authorization_endpoint", baseUrl() + "/authorize", "token_endpoint", baseUrl() + "/token", "userinfo_endpoint", baseUrl() + "/userinfo", "jwks_uri", baseUrl() + "/jwks"));
    }

    private void token(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, Map.of("error", "invalid_request"));
            return;
        }
        try {
            String token = signedToken(codeSubject);
            respond(exchange, 200, Map.of("access_token", token, "id_token", token, "refresh_token", "fixture-refresh", "token_type", "Bearer", "expires_in", 3600));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void userinfo(HttpExchange exchange) throws IOException {
        try {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                respond(exchange, 401, Map.of("error", "invalid_token"));
                return;
            }
            String[] parts = authorization.substring(7).split("\\.", -1);
            if (parts.length != 3) {
                respond(exchange, 401, Map.of("error", "invalid_token"));
                return;
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key.getPublic());
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            var claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2])) || claims.path("exp").asLong() <= Instant.now().getEpochSecond()) {
                respond(exchange, 401, Map.of("error", "invalid_token"));
                return;
            }
            String subject = claims.path("sub").asText();
            String address = email != null ? email : subject + "@example.org";
            respond(exchange, 200, Map.of("sub", subject, "sAMAccountName", subject, "mail", address, "email", address, "displayName", subject, "name", subject, "memberOf", List.of("cn=forged,ou=groups,dc=example,dc=org")));
        } catch (Exception e) {
            respond(exchange, 401, Map.of("error", "invalid_token"));
        }
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return encode(bytes);
    }

    private static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
