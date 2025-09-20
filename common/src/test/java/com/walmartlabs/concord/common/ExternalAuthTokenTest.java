package com.walmartlabs.concord.common;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalAuthTokenTest {

    static final String MOCK_TOKEN = "mock-token";
    static final ObjectMapper MAPPER = new ObjectMapperProvider().get();

    @Test
    void testExpiration() {
        var token = ExternalAuthToken.SimpleToken.builder()
                .token(MOCK_TOKEN)
                .expiresAt(OffsetDateTime.now().minusSeconds((100)))
                .build();

        assertTrue(token.secondsUntilExpiration() < 0);
    }

    @Test
    void testStaticExpiration() {
        var token = ExternalAuthToken.StaticToken.builder()
                .token(MOCK_TOKEN)
                .build();

        assertEquals(Long.MAX_VALUE, token.secondsUntilExpiration());
    }

    @Test
    void testMinimalDeserialization() throws JsonProcessingException {
        var minimalFromJson = MAPPER.readValue("""
                {
                    "token": "mock-token"
                }
                """, ExternalAuthToken.class);

        assertEquals(MOCK_TOKEN, minimalFromJson.token());
    }

    @Test
    void testFullDeserialization() throws JsonProcessingException {
        var fullFromJson = MAPPER.readValue("""
                {
                    "token": "mock-token",
                    "expires_at": "2099-12-31T23:59:59Z",
                    "username": "mock-username"
                }
                """, ExternalAuthToken.class);

        assertEquals(MOCK_TOKEN, fullFromJson.token());
        assertEquals("mock-username", fullFromJson.username());
        var dt = fullFromJson.expiresAt();
        assertNotNull(dt);
        assertEquals(2099, dt.getYear());
    }

    @Test
    void testFullDeserializationMillis() throws JsonProcessingException {
        var fullFromJson = MAPPER.readValue("""
                {
                    "token": "mock-token",
                    "expires_at": "2099-12-31T23:59:59.123Z",
                    "username": "mock-username"
                }
                """, ExternalAuthToken.class);

        assertEquals(MOCK_TOKEN, fullFromJson.token());
        var dt = fullFromJson.expiresAt();
        assertNotNull(dt);
        assertEquals(2099, dt.getYear());
        assertEquals(123, dt.getNano() / 1_000_000);
    }
}
