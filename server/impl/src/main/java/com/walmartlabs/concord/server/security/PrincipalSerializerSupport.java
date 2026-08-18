package com.walmartlabs.concord.server.security;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PrincipalSerializerSupport {

    public static final String USER_PRINCIPAL_TYPE = "user";
    public static final String LDAP_PRINCIPAL_TYPE = "ldap";

    private static final TypeReference<Set<String>> SET_OF_STRINGS = new TypeReference<Set<String>>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_OF_OBJECTS = new TypeReference<Map<String, Object>>() {
    };

    public static JsonNode toJsonNode(ObjectMapper objectMapper, UserPrincipal principal) {
        var data = objectMapper.createObjectNode();
        put(data, "realm", principal.getRealm());
        data.set("user", objectMapper.valueToTree(principal.getUser()));
        return data;
    }

    public static UserPrincipal userPrincipal(ObjectMapper objectMapper, JsonNode data) throws Exception {
        var user = objectMapper.treeToValue(data.get("user"), UserEntry.class);
        return new UserPrincipal(text(data, "realm"), user);
    }

    public static JsonNode toJsonNode(ObjectMapper objectMapper, LdapPrincipal principal) {
        var data = objectMapper.createObjectNode();
        put(data, "username", principal.getUsername());
        put(data, "domain", principal.getDomain());
        put(data, "nameInNamespace", principal.getNameInNamespace());
        put(data, "userPrincipalName", principal.getUserPrincipalName());
        put(data, "displayName", principal.getDisplayName());
        put(data, "email", principal.getEmail());
        data.set("groups", objectMapper.valueToTree(principal.getGroups()));
        data.set("attributes", objectMapper.valueToTree(principal.getAttributes()));
        return data;
    }

    public static LdapPrincipal ldapPrincipal(ObjectMapper objectMapper, JsonNode data) {
        return new LdapPrincipal(
                text(data, "username"),
                text(data, "domain"),
                text(data, "nameInNamespace"),
                text(data, "userPrincipalName"),
                text(data, "displayName"),
                text(data, "email"),
                value(objectMapper, data, "groups", SET_OF_STRINGS),
                value(objectMapper, data, "attributes", MAP_OF_OBJECTS));
    }

    public static void put(ObjectNode data, String key, UUID value) {
        put(data, key, value != null ? value.toString() : null);
    }

    public static void put(ObjectNode data, String key, String value) {
        if (value == null) {
            data.putNull(key);
        } else {
            data.put(key, value);
        }
    }

    public static String text(JsonNode data, String key) {
        var value = data.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    public static UUID uuid(JsonNode data, String key) {
        var value = text(data, key);
        return value != null ? UUID.fromString(value) : null;
    }

    public static boolean bool(JsonNode data, String key) {
        var value = data.get(key);
        return value != null && value.asBoolean();
    }

    public static <T> T value(ObjectMapper objectMapper, JsonNode data, String key, TypeReference<T> type) {
        var value = data.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return objectMapper.convertValue(value, type);
    }

    public static byte[] toBytes(ObjectMapper objectMapper, JsonNode data) {
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode toJsonNode(ObjectMapper objectMapper, byte[] data) {
        try {
            return objectMapper.readTree(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private PrincipalSerializerSupport() {
    }
}
