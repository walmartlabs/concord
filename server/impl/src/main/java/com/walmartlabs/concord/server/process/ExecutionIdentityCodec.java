package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Versioned, grant-free execution identity representation. */
public final class ExecutionIdentityCodec {
    private static final JsonFactory JSON = new JsonFactory();

    private ExecutionIdentityCodec() {
    }

    public static boolean isLegacy(byte[] data) {
        return data != null && data.length >= 2 && (data[0] & 0xff) == 0xac && (data[1] & 0xff) == 0xed;
    }

    public static byte[] encode(ExecutionIdentity identity) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JsonGenerator json = JSON.createGenerator(output)) {
                json.writeStartObject();
                json.writeNumberField("version", 1);
                json.writeStringField("userId", identity.userId().toString());
                json.writeStringField("authenticationProviderId", identity.authenticationProviderId());
                json.writeEndObject();
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode execution identity", e);
        }
    }

    public static ExecutionIdentity decode(byte[] data) {
        if (isLegacy(data)) {
            return LegacyExecutionIdentityReader.read(data);
        }
        if (data == null) {
            throw new IllegalArgumentException("Missing execution identity");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            try (JsonParser json = JSON.createParser(text)) {
                if (json.nextToken() != JsonToken.START_OBJECT) {
                    throw invalid("expected an object");
                }
                Integer version = null;
                String userId = null;
                String provider = null;
                while (json.nextToken() != JsonToken.END_OBJECT) {
                    if (json.currentToken() != JsonToken.FIELD_NAME) {
                        throw invalid("expected a field");
                    }
                    String field = json.currentName();
                    JsonToken value = json.nextToken();
                    switch (field) {
                        case "version":
                            if (version != null || value != JsonToken.VALUE_NUMBER_INT) throw invalid("invalid version");
                            version = json.getIntValue();
                            break;
                        case "userId":
                            if (userId != null || value != JsonToken.VALUE_STRING) throw invalid("invalid userId");
                            userId = json.getText();
                            break;
                        case "authenticationProviderId":
                            if (provider != null || value != JsonToken.VALUE_STRING) throw invalid("invalid authenticationProviderId");
                            provider = json.getText();
                            break;
                        default:
                            throw invalid("unknown field: " + field);
                    }
                }
                if (json.nextToken() != null || version == null || version != 1 || userId == null || provider == null) {
                    throw invalid("missing fields, unsupported version, or trailing data");
                }
                UUID id = UUID.fromString(userId);
                if (!id.toString().equalsIgnoreCase(userId)) throw invalid("invalid userId");
                return new ExecutionIdentity(id, provider);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid execution identity JSON", e);
        }
    }

    private static IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException(reason);
    }
}
