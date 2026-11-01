package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility methods for working with Shiro's security context.
 * Should be the only place where Shiro's API is used directly except for
 * the security filters.
 */
public final class SecurityUtils {

    public static void logout() {
        Subject subject = getSubject();
        if (subject != null) {
            subject.logout();
        }
    }

    public static boolean hasRole(String role) {
        Subject s = getSubject();
        return s.hasRole(role);
    }

    public static boolean isPermitted(String permission) {
        Subject s = getSubject();
        return s.isPermitted(permission);
    }

    public static Subject getSubject() {
        Subject subject = ThreadContext.getSubject();
        if (subject == null) {
            subject = (new Subject.Builder()).buildSubject();
            ThreadContext.bind(subject);
        }
        return subject;
    }

    public static <T> T getCurrent(Class<T> type) {
        SecurityManager securityManager = ThreadContext.getSecurityManager();
        if (securityManager == null) {
            return null;
        }

        Subject subject = getSubject();
        if (subject == null) {
            return null;
        }

        PrincipalCollection principals = subject.getPrincipals();
        if (principals == null) {
            return null;
        }

        return principals.oneByType(type);
    }

    public static <T> T assertCurrent(Class<T> type) {
        T p = getCurrent(type);
        if (p == null) {
            throw new AuthenticationException("Can't determine the current principal (" + type.getName() + ")");
        }
        return p;
    }

    /**
     * Serializes a {@link PrincipalCollection} to JSON bytes.
     * Used for remember-me cookies to avoid Java deserialization risks.
     */
    public static byte[] serializeJson(PrincipalCollection data) {
        Map<String, List<PrincipalPayload>> realms = new LinkedHashMap<>();
        for (String realm : data.getRealmNames()) {
            List<PrincipalPayload> items = new ArrayList<>();
            for (Object p : data.fromRealm(realm)) {
                PrincipalPayload payload = PrincipalPayload.from(p);
                if (payload != null) {
                    items.add(payload);
                }
            }
            if (!items.isEmpty()) {
                realms.put(realm, items);
            }
        }
        var root = new PrincipalCollectionData(FORMAT_VERSION, realms);
        try {
            return MAPPER.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new RuntimeException("Error serializing principals to JSON", e);
        }
    }

    /**
     * Deserializes a {@link PrincipalCollection} from JSON bytes.
     * Returns {@link Optional#empty()} if the data is not in JSON format (legacy Java serialization).
     */
    public static Optional<PrincipalCollection> deserializeJson(byte[] data) {
        if (data.length == 0 || data[0] != '{') {
            return Optional.empty();
        }

        PrincipalCollectionData root;
        try {
            root = MAPPER.readValue(data, PrincipalCollectionData.class);
        } catch (IOException e) {
            return Optional.empty();
        }

        if (!FORMAT_VERSION.equals(root.format())) {
            return Optional.empty();
        }

        Map<String, List<PrincipalPayload>> realms = root.realms();
        if (realms == null) {
            return Optional.empty();
        }

        SimplePrincipalCollection coll = new SimplePrincipalCollection();
        for (var realmEntry : realms.entrySet()) {
            for (var item : realmEntry.getValue()) {
                Object p = item.toPrincipal();
                if (p != null) {
                    coll.add(p, realmEntry.getKey());
                }
            }
        }
        return Optional.of(coll);
    }

    /**
     * Top-level container for remember-me principal data.
     */
    record PrincipalCollectionData(
            @JsonProperty("_f") String format,
            @JsonProperty("r") Map<String, List<PrincipalPayload>> realms) {
    }

    /**
     * Polymorphic payload for a single remember-me principal.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "t")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PrincipalPayload.Up.class, name = "up"),
            @JsonSubTypes.Type(value = PrincipalPayload.Api.class, name = "api")
    })
    sealed interface PrincipalPayload permits PrincipalPayload.Up, PrincipalPayload.Api {

        Object toPrincipal();

        static PrincipalPayload from(Object p) {
            if (p instanceof UsernamePasswordToken t) {
                return new Up(
                        t.getUsername(),
                        Base64.getEncoder().encodeToString(
                                new String(t.getPassword()).getBytes(StandardCharsets.UTF_8)),
                        t.isRememberMe(),
                        t.getHost());
            }
            if (p instanceof ApiKey k) {
                return new Api(
                        k.getKeyId().toString(),
                        k.getUserId().toString(),
                        k.getKey(),
                        k.isRememberMe());
            }
            return null;
        }

        record Up(
                @JsonProperty("u") String username,
                @JsonProperty("p") String password,
                @JsonProperty("rm") boolean rememberMe,
                @JsonProperty("h") String host
        ) implements PrincipalPayload {
            @Override
            public Object toPrincipal() {
                byte[] pb = Base64.getDecoder().decode(password);
                char[] pwd = new String(pb, StandardCharsets.UTF_8).toCharArray();
                return new UsernamePasswordToken(username, pwd, rememberMe, host);
            }
        }

        record Api(
                @JsonProperty("kid") String keyId,
                @JsonProperty("uid") String userId,
                @JsonProperty("k") String key,
                @JsonProperty("rm") boolean rememberMe
        ) implements PrincipalPayload {
            @Override
            public Object toPrincipal() {
                return new ApiKey(
                        UUID.fromString(keyId),
                        UUID.fromString(userId),
                        key,
                        rememberMe);
            }
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FORMAT_VERSION = "1";

    // Legacy Java serialization, kept for server-side uses (ProcessSecurityContext)
    public static byte[] serialize(PrincipalCollection data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static Optional<PrincipalCollection> deserialize(byte[] data) {
        InputStream in = new ByteArrayInputStream(data);
        return deserialize(in);
    }

    public static Optional<PrincipalCollection> deserialize(InputStream in) {
        try (ObjectInputStream ois = new ObjectInputStream(in)) {
            return Optional.of((PrincipalCollection) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static AuthorizationInfo toAuthorizationInfo(PrincipalCollection principals) {
        return toAuthorizationInfo(principals, null);
    }

    public static AuthorizationInfo toAuthorizationInfo(PrincipalCollection principals, List<String> extraRoles) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();

        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (p == null) {
            return i;
        }

        UserEntry u = p.getUser();
        Set<RoleEntry> roles = u.getRoles();
        if (roles != null) {
            roles.forEach(r -> {
                i.addRole(r.getName());

                Set<String> permissions = r.getPermissions();
                if (permissions != null) {
                    permissions.forEach(i::addStringPermission);
                }
            });
        }

        if (extraRoles != null) {
            extraRoles.forEach(i::addRole);
        }

        return i;
    }

    private SecurityUtils() {
    }
}
