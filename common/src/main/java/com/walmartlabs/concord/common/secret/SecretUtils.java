package com.walmartlabs.concord.common.secret;

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

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.security.*;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

public final class SecretUtils {

    public static byte[] encrypt(byte[] input, byte[] password, byte[] salt) {
        return encrypt(input, password, salt, HashAlgorithm.LEGACY_MD5);
    }

    public static byte[] encrypt(byte[] input, byte[] password, byte[] salt, HashAlgorithm hashAlgorithm) {
        try {
            return encrypt(new ByteArrayInputStream(input), password, salt, hashAlgorithm).readAllBytes();
        } catch (IOException e) {
            throw new SecurityException("Error encrypting a secret: " + e);
        }
    }

    public static InputStream encrypt(InputStream input, byte[] password, byte[] salt) {
        return encrypt(input, password, salt, HashAlgorithm.LEGACY_MD5);
    }

    public static InputStream encrypt(InputStream input, byte[] password, byte[] salt, HashAlgorithm hashAlgorithm) {
        try {
            Cipher c = init(password, salt, Cipher.ENCRYPT_MODE, hashAlgorithm);
            return new CipherInputStream(input, c);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error encrypting a secret: " + e);
        }
    }

    public static byte[] decrypt(byte[] input, byte[] password, byte[] salt) {
        return decrypt(input, password, salt, HashAlgorithm.LEGACY_MD5);
    }

    public static byte[] decrypt(byte[] input, byte[] password, byte[] salt, HashAlgorithm hashAlgorithm) {
        try {
            InputStream out = decrypt(new ByteArrayInputStream(input), password, salt, hashAlgorithm);
            return out.readAllBytes();
        } catch (IOException e) {
            Throwable t = e.getCause() == null ? e : e.getCause();
            if (t instanceof BadPaddingException) {
                throw new SecurityException("Error decrypting a secret: " + t.getMessage() + ". Invalid input data and/or a password.");
            }
            throw new SecurityException("Error decrypting a secret: " + e.getMessage(), t);
        }
    }

    public static InputStream decrypt(InputStream input, byte[] password, byte[] salt) {
        return decrypt(input, password, salt, HashAlgorithm.LEGACY_MD5);
    }

    public static InputStream decrypt(InputStream input, byte[] password, byte[] salt, HashAlgorithm hashAlgorithm) {
        try {
            Cipher c = init(password, salt, Cipher.DECRYPT_MODE, hashAlgorithm);
            return new CipherInputStream(input, c);
        } catch (BadPaddingException e) {
            throw new SecurityException("Error decrypting a secret: " + e.getMessage() + ". Invalid input data and/or a password.");
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error decrypting a secret: " + e.getMessage());
        }
    }

    public static byte[] hash(byte[] in, byte[] salt, HashAlgorithm hashAlgorithm) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(hashAlgorithm.getName());
        digest.update(salt);
        return in != null ? digest.digest(in) : digest.digest();
    }

    private static Cipher init(byte[] password, byte[] salt, int mode, HashAlgorithm hashAlgorithm) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES");

        byte[] key = hash(password, salt, hashAlgorithm);
        SecretKeySpec k = new SecretKeySpec(key, "AES");

        c.init(mode, k);
        return c;
    }

    public static byte[] generateSalt(int size) {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[size];
        sr.nextBytes(bytes);
        return bytes;
    }

    private SecretUtils() {
    }

    private static String generateJwtToken(String clientId, PrivateKey privateKey, long expirationTimeInSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + (expirationTimeInSeconds * 1000));

        return Jwts.builder()
                .issuer(clientId)
                .subject(clientId)
                .audience().add("concord").and()
                .issuedAt(now)
                .expiration(expiration)
                .id(UUID.randomUUID().toString())
                .signWith(privateKey)
                .compact();
    }

    public static String generateGitHubInstallationToken(String appId, PrivateKey privateKey, String installationId) throws IOException, InterruptedException {
        // Generate JWT token for GitHub App authentication (expires in 10 minutes)
        String jwtToken = generateJwtToken(appId, privateKey, 3600);

        // Make API call to generate installation access token
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/app/installations/" + installationId + "/access_tokens"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new IOException("Failed to generate installation token: " + response.statusCode() + " " + response.body());
        }

        // Parse the access token from JSON response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(response.body());
        return jsonNode.get("token").asText();
    }
}
