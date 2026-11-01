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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationVisibility;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
        data.set("user", objectMapper.valueToTree(toUserSnapshot(principal.getUser())));
        return data;
    }

    public static UserPrincipal userPrincipal(ObjectMapper objectMapper, JsonNode data) throws Exception {
        var snapshot = objectMapper.treeToValue(data.get("user"), UserSnapshot.class);
        var user = snapshot != null ? toUserEntry(snapshot) : null;
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

    // region durable user schema
    // The snapshot records below fix the durable JSON schema of UserPrincipal payloads. Their field names are
    // the persisted property names; changes here are format changes and require a versioned migration path.

    private static UserSnapshot toUserSnapshot(UserEntry user) {
        return new UserSnapshot(
                user.getId(),
                user.getName(),
                user.getDomain(),
                user.getDisplayName(),
                toSnapshots(user.getOrgs(), PrincipalSerializerSupport::toOrganizationSnapshot),
                user.getType() != null ? user.getType().name() : null,
                user.getEmail(),
                toSnapshots(user.getRoles(), PrincipalSerializerSupport::toRoleSnapshot),
                user.isDisabled(),
                user.getDisabledDate(),
                user.isPermanentlyDisabled());
    }

    private static UserEntry toUserEntry(UserSnapshot s) {
        return new UserEntry(
                s.id(),
                s.name(),
                s.domain(),
                s.displayName(),
                toEntries(s.orgs(), PrincipalSerializerSupport::toOrganizationEntry),
                s.type() != null ? UserType.valueOf(s.type()) : null,
                s.email(),
                toEntries(s.roles(), PrincipalSerializerSupport::toRoleEntry),
                s.disabled(),
                s.disabledDate(),
                s.permanentlyDisabled());
    }

    private static RoleSnapshot toRoleSnapshot(RoleEntry r) {
        return new RoleSnapshot(r.getId(), r.getName(), r.getPermissions());
    }

    private static RoleEntry toRoleEntry(RoleSnapshot s) {
        return new RoleEntry(s.id(), s.name(), s.permissions());
    }

    private static OrganizationSnapshot toOrganizationSnapshot(OrganizationEntry o) {
        return new OrganizationSnapshot(
                o.getId(),
                o.getName(),
                o.getOwner() != null ? toOwnerSnapshot(o.getOwner()) : null,
                o.getVisibility() != null ? o.getVisibility().name() : null,
                o.getMeta(),
                o.getCfg());
    }

    private static OrganizationEntry toOrganizationEntry(OrganizationSnapshot s) {
        return new OrganizationEntry(
                s.id(),
                s.name(),
                s.owner() != null ? toEntityOwner(s.owner()) : null,
                s.visibility() != null ? OrganizationVisibility.valueOf(s.visibility()) : null,
                s.meta(),
                s.cfg());
    }

    private static OwnerSnapshot toOwnerSnapshot(EntityOwner o) {
        return new OwnerSnapshot(
                o.id(),
                o.username(),
                o.userDomain(),
                o.displayName(),
                o.userType() != null ? o.userType().name() : null);
    }

    private static EntityOwner toEntityOwner(OwnerSnapshot s) {
        return EntityOwner.builder()
                .id(s.id())
                .username(s.username())
                .userDomain(s.userDomain())
                .displayName(s.displayName())
                .userType(s.userType() != null ? UserType.valueOf(s.userType()) : null)
                .build();
    }

    private static <S, E> Set<E> toSnapshots(Set<S> entries, Function<S, E> mapper) {
        if (entries == null) {
            return null;
        }
        var result = new LinkedHashSet<E>(entries.size());
        for (var entry : entries) {
            result.add(entry != null ? mapper.apply(entry) : null);
        }
        return result;
    }

    private static <S, E> Set<E> toEntries(Set<S> snapshots, Function<S, E> mapper) {
        if (snapshots == null) {
            return null;
        }
        var result = new LinkedHashSet<E>(snapshots.size());
        for (var snapshot : snapshots) {
            result.add(snapshot != null ? mapper.apply(snapshot) : null);
        }
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record UserSnapshot(UUID id,
                                String name,
                                String domain,
                                String displayName,
                                Set<OrganizationSnapshot> orgs,
                                String type,
                                String email,
                                Set<RoleSnapshot> roles,
                                boolean disabled,
                                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX") OffsetDateTime disabledDate,
                                boolean permanentlyDisabled) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record RoleSnapshot(UUID id, String name, Set<String> permissions) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record OrganizationSnapshot(UUID id,
                                        String name,
                                        OwnerSnapshot owner,
                                        String visibility,
                                        Map<String, Object> meta,
                                        Map<String, Object> cfg) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record OwnerSnapshot(UUID id,
                                 String username,
                                 String userDomain,
                                 String displayName,
                                 String userType) {
    }

    // endregion

    private PrincipalSerializerSupport() {
    }
}
