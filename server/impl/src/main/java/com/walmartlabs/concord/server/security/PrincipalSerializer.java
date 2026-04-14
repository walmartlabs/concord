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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.github.GithubKey;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class PrincipalSerializer {

    private static final String SNAPSHOT_TYPE = "concord.security.principal-collection";
    private static final int SNAPSHOT_VERSION = 1;
    private static final String OIDC_TOKEN_CLASS = "com.walmartlabs.concord.server.plugins.oidc.OidcToken";
    private static final String OIDC_PROFILE_CLASS = "com.walmartlabs.concord.server.plugins.oidc.UserProfile";
    private static final String SSO_TOKEN_CLASS = "com.walmartlabs.concord.server.plugins.pfedsso.SsoToken";

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    static byte[] serialize(PrincipalCollection data) {
        Snapshot snapshot = new Snapshot();
        snapshot.type = SNAPSHOT_TYPE;
        snapshot.version = SNAPSHOT_VERSION;
        snapshot.realms = new ArrayList<>();

        if (data != null) {
            for (String realmName : data.getRealmNames()) {
                RealmSnapshot realm = new RealmSnapshot();
                realm.name = realmName;
                realm.principals = new ArrayList<>();

                for (Object principal : data.fromRealm(realmName)) {
                    PrincipalSnapshot p = toSnapshot(principal);
                    if (p != null) {
                        realm.principals.add(p);
                    }
                }

                if (!realm.principals.isEmpty()) {
                    snapshot.realms.add(realm);
                }
            }
        }

        try {
            return objectMapper.writeValueAsBytes(snapshot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Optional<PrincipalCollection> deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }

        if (isJson(data)) {
            return deserializeJson(data);
        }

        return deserializeLegacy(data);
    }

    static Optional<PrincipalCollection> deserialize(InputStream in) {
        try {
            return deserialize(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<PrincipalCollection> deserializeJson(byte[] data) {
        try {
            Snapshot snapshot = objectMapper.readValue(data, Snapshot.class);
            if (!SNAPSHOT_TYPE.equals(snapshot.type)) {
                throw new IllegalArgumentException("Unsupported principal snapshot type: " + snapshot.type);
            }
            if (snapshot.version != SNAPSHOT_VERSION) {
                throw new IllegalArgumentException("Unsupported principal snapshot version: " + snapshot.version);
            }

            SimplePrincipalCollection result = new SimplePrincipalCollection();
            if (snapshot.realms != null) {
                for (RealmSnapshot realm : snapshot.realms) {
                    if (realm.principals == null) {
                        continue;
                    }
                    for (PrincipalSnapshot p : realm.principals) {
                        Object principal = fromSnapshot(p);
                        if (principal != null) {
                            result.add(principal, realm.name);
                        }
                    }
                }
            }

            return Optional.of(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<PrincipalCollection> deserializeLegacy(byte[] data) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return Optional.of((PrincipalCollection) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] legacySerialize(PrincipalCollection data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private static boolean isJson(byte[] data) {
        for (byte b : data) {
            if (Character.isWhitespace((char) b)) {
                continue;
            }
            return b == '{';
        }
        return false;
    }

    private static PrincipalSnapshot toSnapshot(Object principal) {
        if (principal instanceof SessionKeyPrincipal) {
            return null;
        } else if (principal instanceof UserPrincipal) {
            return userPrincipal((UserPrincipal) principal);
        } else if (principal instanceof LdapPrincipal) {
            return ldapPrincipal((LdapPrincipal) principal);
        } else if (principal instanceof ApiKey) {
            return apiKey((ApiKey) principal);
        } else if (principal instanceof UsernamePasswordToken) {
            return usernamePassword((UsernamePasswordToken) principal);
        } else if (principal instanceof GithubKey) {
            return githubKey((GithubKey) principal);
        } else if (principal != null && OIDC_TOKEN_CLASS.equals(principal.getClass().getName())) {
            return oidcToken(principal);
        } else if (principal != null && SSO_TOKEN_CLASS.equals(principal.getClass().getName())) {
            return ssoToken(principal);
        }

        throw new IllegalArgumentException("Unsupported principal type: " + principal.getClass().getName());
    }

    private static Object fromSnapshot(PrincipalSnapshot p) throws Exception {
        switch (p.type) {
            case "user":
                UserEntry user = objectMapper.treeToValue(p.data.get("user"), UserEntry.class);
                return new UserPrincipal(text(p.data, "realm"), user);
            case "ldap":
                return new LdapPrincipal(
                        text(p.data, "username"),
                        text(p.data, "domain"),
                        text(p.data, "nameInNamespace"),
                        text(p.data, "userPrincipalName"),
                        text(p.data, "displayName"),
                        text(p.data, "email"),
                        value(p.data, "groups", new TypeReference<Set<String>>() {
                        }),
                        value(p.data, "attributes", new TypeReference<Map<String, Object>>() {
                        }));
            case "apiKey":
                return new ApiKey(
                        uuid(p.data, "keyId"),
                        uuid(p.data, "userId"),
                        text(p.data, "key"),
                        bool(p.data, "rememberMe"));
            case "usernamePassword":
                String username = text(p.data, "username");
                String password = text(p.data, "password");
                if (username == null && password == null) {
                    return new UsernamePasswordToken();
                }
                return new UsernamePasswordToken(username, password != null ? password.toCharArray() : null, bool(p.data, "rememberMe"));
            case "githubKey":
                return new GithubKey(text(p.data, "key"), uuid(p.data, "projectId"), text(p.data, "repoToken"));
            case "oidcToken":
                return oidcToken(p.data);
            case "ssoToken":
                return ssoToken(p.data);
            default:
                throw new IllegalArgumentException("Unsupported principal snapshot type: " + p.type);
        }
    }

    private static PrincipalSnapshot userPrincipal(UserPrincipal principal) {
        ObjectNode data = objectMapper.createObjectNode();
        put(data, "realm", principal.getRealm());
        data.set("user", objectMapper.valueToTree(principal.getUser()));
        return new PrincipalSnapshot("user", data);
    }

    private static PrincipalSnapshot ldapPrincipal(LdapPrincipal principal) {
        ObjectNode data = objectMapper.createObjectNode();
        put(data, "username", principal.getUsername());
        put(data, "domain", principal.getDomain());
        put(data, "nameInNamespace", principal.getNameInNamespace());
        put(data, "userPrincipalName", principal.getUserPrincipalName());
        put(data, "displayName", principal.getDisplayName());
        put(data, "email", principal.getEmail());
        data.set("groups", objectMapper.valueToTree(principal.getGroups()));
        data.set("attributes", objectMapper.valueToTree(principal.getAttributes()));
        return new PrincipalSnapshot("ldap", data);
    }

    private static PrincipalSnapshot apiKey(ApiKey principal) {
        ObjectNode data = objectMapper.createObjectNode();
        put(data, "keyId", principal.getKeyId());
        put(data, "userId", principal.getUserId());
        put(data, "key", principal.getKey());
        data.put("rememberMe", principal.isRememberMe());
        return new PrincipalSnapshot("apiKey", data);
    }

    private static PrincipalSnapshot usernamePassword(UsernamePasswordToken principal) {
        ObjectNode data = objectMapper.createObjectNode();
        put(data, "username", principal.getUsername());
        char[] password = principal.getPassword();
        put(data, "password", password != null ? new String(password) : null);
        data.put("rememberMe", principal.isRememberMe());
        return new PrincipalSnapshot("usernamePassword", data);
    }

    private static PrincipalSnapshot githubKey(GithubKey principal) {
        ObjectNode data = objectMapper.createObjectNode();
        put(data, "key", principal.getKey());
        put(data, "projectId", principal.getProjectId());
        put(data, "repoToken", principal.getRepoToken());
        return new PrincipalSnapshot("githubKey", data);
    }

    private static PrincipalSnapshot oidcToken(Object principal) {
        ObjectNode data = objectMapper.createObjectNode();
        data.set("profile", objectMapper.valueToTree(invoke(principal, "getProfile")));
        return new PrincipalSnapshot("oidcToken", data);
    }

    private static Object oidcToken(JsonNode data) throws Exception {
        Class<?> profileClass = Class.forName(OIDC_PROFILE_CLASS);
        Object profile = objectMapper.treeToValue(data.get("profile"), profileClass);
        Class<?> tokenClass = Class.forName(OIDC_TOKEN_CLASS);
        return tokenClass.getConstructor(profileClass).newInstance(profile);
    }

    private static PrincipalSnapshot ssoToken(Object principal) {
        ObjectNode data = objectMapper.createObjectNode();
        put(data, "username", (String) invoke(principal, "getUsername"));
        put(data, "domain", (String) invoke(principal, "getDomain"));
        put(data, "displayName", (String) invoke(principal, "getDisplayName"));
        put(data, "mail", (String) invoke(principal, "getMail"));
        put(data, "userPrincipalName", (String) invoke(principal, "getUserPrincipalName"));
        put(data, "nameInNamespace", (String) invoke(principal, "getNameInNamespace"));
        data.set("groups", objectMapper.valueToTree(invoke(principal, "getGroups")));
        return new PrincipalSnapshot("ssoToken", data);
    }

    private static Object ssoToken(JsonNode data) throws Exception {
        Class<?> tokenClass = Class.forName(SSO_TOKEN_CLASS);
        Set<String> groups = value(data, "groups", new TypeReference<Set<String>>() {
        });
        return tokenClass.getConstructor(String.class, String.class, String.class, String.class, String.class, String.class, Set.class)
                .newInstance(
                        text(data, "username"),
                        text(data, "domain"),
                        text(data, "displayName"),
                        text(data, "mail"),
                        text(data, "userPrincipalName"),
                        text(data, "nameInNamespace"),
                        groups);
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void put(ObjectNode data, String key, UUID value) {
        put(data, key, value != null ? value.toString() : null);
    }

    private static void put(ObjectNode data, String key, String value) {
        if (value == null) {
            data.putNull(key);
        } else {
            data.put(key, value);
        }
    }

    private static String text(JsonNode data, String key) {
        JsonNode value = data.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static UUID uuid(JsonNode data, String key) {
        String value = text(data, key);
        return value != null ? UUID.fromString(value) : null;
    }

    private static boolean bool(JsonNode data, String key) {
        JsonNode value = data.get(key);
        return value != null && value.asBoolean();
    }

    private static <T> T value(JsonNode data, String key, TypeReference<T> type) {
        JsonNode value = data.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return objectMapper.convertValue(value, type);
    }

    private static final class Snapshot {

        public String type;
        public int version;
        public List<RealmSnapshot> realms;
    }

    private static final class RealmSnapshot {

        public String name;
        public List<PrincipalSnapshot> principals;
    }

    private static final class PrincipalSnapshot {

        public String type;
        public JsonNode data;

        public PrincipalSnapshot() {
        }

        private PrincipalSnapshot(String type, JsonNode data) {
            this.type = type;
            this.data = data;
        }
    }

    private PrincipalSerializer() {
    }
}
