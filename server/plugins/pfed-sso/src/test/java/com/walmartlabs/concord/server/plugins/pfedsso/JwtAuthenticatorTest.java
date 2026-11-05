package com.walmartlabs.concord.server.plugins.pfedsso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtAuthenticatorTest {

    @Test
    public void selectsFirstMatchingKeyAndPreservesItsFields() throws IOException {
        String keys = "{\"keys\":["
                + "{\"kid\":\"target\",\"kty\":\"other\"},"
                + "{\"kid\":\"target\",\"kty\":\"EC\",\"x\":\"ec\"},"
                + "{\"kid\":\"target\",\"kty\":\"RSA\",\"n\":\"first\",\"extra\":{\"a\":1}},"
                + "{\"kid\":\"target\",\"kty\":\"RSA\",\"n\":\"second\"}]}";

        assertEquals("{\"kid\":\"target\",\"kty\":\"RSA\",\"n\":\"first\",\"extra\":{\"a\":1}}",
                JwtAuthenticator.getMatchedSigningKey(keys, "target", "RS256"));
        assertEquals("{\"kid\":\"target\",\"kty\":\"EC\",\"x\":\"ec\"}",
                JwtAuthenticator.getMatchedSigningKey(keys, "target", "ES256"));
        assertNull(JwtAuthenticator.getMatchedSigningKey(keys, "absent", "RS256"));
        assertNull(JwtAuthenticator.getMatchedSigningKey(keys, "target", "RS384"));
        assertNull(JwtAuthenticator.getMatchedSigningKey(null, "target", "RS256"));
        assertNull(JwtAuthenticator.getMatchedSigningKey("{\"keys\":[]}", "target", "RS256"));
    }

    @Test
    public void acceptsPrimitiveIdentifiersAsStrings() throws IOException {
        assertEquals("{\"kid\":123,\"kty\":\"RSA\"}",
                JwtAuthenticator.getMatchedSigningKey("{\"keys\":[{\"kid\":123,\"kty\":\"RSA\"}]}", "123", "RS256"));
    }

    @Test
    public void rejectsMalformedKeySetsAndMissingFields() {
        String[] malformed = {
                "", "{", "{}", "null", "[]", "{\"keys\":null}", "{\"keys\":{}}",
                "{\"keys\":[]} {}", "{\"keys\":[null]}", "{\"keys\":[1]}",
                "{\"keys\":[{\"kty\":\"RSA\"}]}", "{\"keys\":[{\"kid\":\"target\"}]}",
                "{\"keys\":[{\"kid\":null,\"kty\":\"RSA\"}]}",
                "{\"keys\":[{\"kid\":\"target\",\"kty\":null}]}",
                "{\"keys\":[{\"kid\":{},\"kty\":\"RSA\"}]}",
                "{\"keys\":[{\"kid\":\"target\",\"kty\":[]}]}",
                "{\"keys\":[{\"kty\":\"unknown\"}]}"
        };
        for (String keys : malformed) {
            assertThrows(IOException.class, () -> JwtAuthenticator.getMatchedSigningKey(keys, "target", "RS256"), keys);
        }
    }
}
