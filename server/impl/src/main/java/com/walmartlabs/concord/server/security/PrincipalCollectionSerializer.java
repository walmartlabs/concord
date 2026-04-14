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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serializes {@link PrincipalCollection}s into a stable JSON snapshot by delegating to
 * {@link PrincipalSerializer} implementations registered for each principal type.
 *
 * <p>Collections containing principal types without a registered serializer (e.g. principals produced by a plugin
 * that doesn't ship a {@link PrincipalSerializer}) are serialized in the legacy Java-serialization format, which
 * is readable by both older and newer servers. Deserialization accepts both formats.
 *
 * <p>Note: the JSON format is only understood by servers that include this class. During a mixed-version upgrade,
 * snapshots written by a newer node cannot be read by nodes running older code - such processes can only be
 * resumed once the upgrade is complete.
 */
public class PrincipalCollectionSerializer {

    private static final String SNAPSHOT_TYPE = "concord.security.principal-collection";
    private static final int SNAPSHOT_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final Set<PrincipalSerializer<?>> serializers;
    private final Map<String, PrincipalSerializer<?>> serializersByType;

    @Inject
    public PrincipalCollectionSerializer(ObjectMapper objectMapper, Set<PrincipalSerializer<?>> serializers) {
        this.objectMapper = objectMapper;
        this.serializers = serializers;
        this.serializersByType = serializers.stream()
                .collect(Collectors.toMap(PrincipalSerializer::type, Function.identity(), (a, b) -> {
                    throw new IllegalArgumentException("Duplicate principal serializer for type: " + a.type());
                }));
    }

    public byte[] serialize(PrincipalCollection data) {
        if (usesLegacySerialization(data)) {
            return legacySerialize(data);
        }

        var snapshot = new Snapshot(SNAPSHOT_TYPE, SNAPSHOT_VERSION, serializedPrincipals(data));

        try {
            return objectMapper.writeValueAsBytes(snapshot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<SerializedPrincipal> serializedPrincipals(PrincipalCollection data) {
        var result = new ArrayList<SerializedPrincipal>();
        if (data != null) {
            for (var realmName : realmNames(data)) {
                var principals = (Collection<?>) data.fromRealm(realmName);
                if (principals.isEmpty()) {
                    continue;
                }

                var records = principals.stream()
                        .map(p -> new PrincipalRecord(p, getSerializer(p)))
                        .toList();
                var normalizedRealmName = realmName(realmName, records);

                for (var record : records) {
                    result.add(new SerializedPrincipal(
                            normalizedRealmName,
                            record.serializer().type(),
                            serialize(record.serializer(), record.principal())));
                }
            }
        }

        return result;
    }

    /**
     * Returns {@code true} if {@code data} contains at least one principal type without a registered serializer.
     * Such collections are kept in the legacy Java-serialization format so that both older and newer servers
     * can read them (e.g. processes started before a plugin shipped its serializer).
     */
    private boolean usesLegacySerialization(PrincipalCollection data) {
        if (data == null) {
            return false;
        }

        for (var realmName : realmNames(data)) {
            for (var principal : (Collection<?>) data.fromRealm(realmName)) {
                if (findSerializer(principal) == null) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Set<String> realmNames(PrincipalCollection data) {
        // Shiro returns null for empty collections
        var realmNames = data.getRealmNames();
        return realmNames != null ? realmNames : Set.of();
    }

    public Optional<PrincipalCollection> deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }

        if (isJson(data)) {
            return deserializeJson(data);
        }

        return deserializeLegacy(data);
    }

    public Optional<PrincipalCollection> deserialize(InputStream in) {
        try {
            return deserialize(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] legacySerialize(PrincipalCollection data) {
        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private Optional<PrincipalCollection> deserializeJson(byte[] data) {
        try {
            var snapshot = objectMapper.readValue(data, Snapshot.class);
            if (!SNAPSHOT_TYPE.equals(snapshot.type())) {
                throw new IllegalArgumentException("Unsupported principal snapshot type: " + snapshot.type());
            }
            if (snapshot.version() != SNAPSHOT_VERSION) {
                throw new IllegalArgumentException("Unsupported principal snapshot version: " + snapshot.version());
            }

            var result = new SimplePrincipalCollection();
            if (snapshot.principals() != null) {
                for (var p : snapshot.principals()) {
                    var serializer = getSerializer(p.type());
                    var principal = serializer.deserialize(p.data());
                    if (principal != null) {
                        result.add(principal, p.realm());
                    }
                }
            }

            return Optional.of(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<PrincipalCollection> deserializeLegacy(byte[] data) {
        try (var ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return Optional.of((PrincipalCollection) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private PrincipalSerializer<?> findSerializer(Object principal) {
        var result = serializers.stream()
                .filter(serializer -> serializer.supports(principal))
                .toList();
        if (result.size() == 1) {
            return result.get(0);
        }
        if (result.isEmpty()) {
            return null;
        }
        throw new IllegalArgumentException("Ambiguous principal serializers for type: " + typeName(principal));
    }

    private PrincipalSerializer<?> getSerializer(Object principal) {
        var serializer = findSerializer(principal);
        if (serializer == null) {
            throw new IllegalArgumentException("Unsupported principal type: " + typeName(principal));
        }
        return serializer;
    }

    private PrincipalSerializer<?> getSerializer(String type) {
        var serializer = serializersByType.get(type);
        if (serializer == null) {
            throw new IllegalArgumentException("Unsupported principal snapshot type: " + type);
        }
        return serializer;
    }

    private static String realmName(String fallback, List<PrincipalRecord> records) {
        var realmNames = records.stream()
                .flatMap(record -> realmName(record.serializer(), record.principal()).stream())
                .collect(Collectors.toSet());
        if (realmNames.isEmpty()) {
            return fallback;
        }
        if (realmNames.size() == 1) {
            return realmNames.iterator().next();
        }
        throw new IllegalArgumentException("Ambiguous principal realms: " + realmNames);
    }

    private static boolean isJson(byte[] data) {
        for (var b : data) {
            if (Character.isWhitespace((char) b)) {
                continue;
            }
            return b == '{';
        }
        return false;
    }

    private static String typeName(Object principal) {
        return principal != null ? principal.getClass().getName() : "null";
    }

    private static <T> byte[] serialize(PrincipalSerializer<T> serializer, Object principal) {
        return serializer.serialize(serializer.principalType().cast(principal));
    }

    private static <T> Optional<String> realmName(PrincipalSerializer<T> serializer, Object principal) {
        return serializer.realmName(serializer.principalType().cast(principal));
    }

    private record PrincipalRecord(Object principal, PrincipalSerializer<?> serializer) {
    }

    private record Snapshot(String type, int version, List<SerializedPrincipal> principals) {
    }

    private record SerializedPrincipal(String realm, String type, byte[] data) {
    }
}
