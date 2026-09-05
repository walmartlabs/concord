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
import com.walmartlabs.concord.server.cfg.PrincipalSerializationConfiguration;
import com.walmartlabs.concord.server.sdk.security.PrincipalSerializer;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Serializes {@link PrincipalCollection}s into a stable JSON snapshot by delegating to
 * {@link PrincipalSerializer} implementations registered for each principal's exact class.
 *
 * <p>Writing requires a registered serializer for every concrete principal class in the collection;
 * principals without one are rejected instead of being silently downgraded or dropped. Each
 * {@link SerializedPrincipal} record preserves the realm membership exactly as stored in the
 * collection.
 *
 * <p>The {@code principalSerialization.legacyWriteEnabled} configuration flag switches writing to the legacy
 * Java-serialization format for rolling upgrades, so that nodes running older code can still read what the
 * upgraded nodes write. The format is never switched implicitly by an unsupported type or a codec failure.
 * Deserialization accepts both formats regardless of the flag; see
 * {@link PrincipalSerializationConfiguration} for the upgrade procedure.
 */
public class PrincipalCollectionSerializer {

    private static final String SNAPSHOT_TYPE = "concord.security.principal-collection";
    private static final int SNAPSHOT_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final boolean legacyWriteEnabled;
    private final Map<Class<?>, PrincipalSerializer<?>> serializersByClass;
    private final Map<String, PrincipalSerializer<?>> serializersByType;

    @Inject
    public PrincipalCollectionSerializer(ObjectMapper objectMapper,
                                         Set<PrincipalSerializer<?>> serializers,
                                         PrincipalSerializationConfiguration cfg) {
        this.objectMapper = objectMapper;
        this.legacyWriteEnabled = cfg.isLegacyWriteEnabled();
        this.serializersByClass = new HashMap<>();
        this.serializersByType = new HashMap<>();
        for (var serializer : serializers) {
            var type = serializer.type();
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Principal serializer type must not be null or blank");
            }

            var principalType = serializer.principalType();
            if (principalType == null) {
                throw new IllegalArgumentException("Principal serializer '" + type + "' must declare a principal class");
            }

            if (serializersByType.putIfAbsent(type, serializer) != null) {
                throw new IllegalArgumentException("Duplicate principal serializer for type: " + type);
            }
            if (serializersByClass.putIfAbsent(principalType, serializer) != null) {
                throw new IllegalArgumentException("Duplicate principal serializer for class: " + principalType.getName());
            }
        }
    }

    public byte[] serialize(PrincipalCollection data) {
        var collection = data != null ? data : new SimplePrincipalCollection();

        if (legacyWriteEnabled) {
            return legacySerialize(collection);
        }

        var snapshot = new Snapshot(SNAPSHOT_TYPE, SNAPSHOT_VERSION, serializedPrincipals(collection));

        try {
            return objectMapper.writeValueAsBytes(snapshot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private List<SerializedPrincipal> serializedPrincipals(PrincipalCollection data) {
        var result = new ArrayList<SerializedPrincipal>();
        for (var realmName : realmNames(data)) {
            for (var principal : (Collection<?>) data.fromRealm(realmName)) {
                var serializer = getSerializer(principal);
                result.add(new SerializedPrincipal(realmName, serializer.type(), serialize(serializer, principal)));
            }
        }
        return result;
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
                    if (principal == null) {
                        throw new IllegalArgumentException("Principal serializer for type '" + p.type() + "' returned null");
                    }
                    if (principal.getClass() != serializer.principalType()) {
                        throw new IllegalArgumentException("Principal serializer for type '" + p.type()
                                + "' returned an unexpected class: " + principal.getClass().getName());
                    }
                    result.add(principal, p.realm());
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

    private PrincipalSerializer<?> getSerializer(Object principal) {
        var serializer = serializersByClass.get(principal.getClass());
        if (serializer == null) {
            throw new IllegalArgumentException("Unsupported principal type: " + principal.getClass().getName());
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

    private static boolean isJson(byte[] data) {
        for (var b : data) {
            if (Character.isWhitespace((char) b)) {
                continue;
            }
            return b == '{';
        }
        return false;
    }

    private static <T> byte[] serialize(PrincipalSerializer<T> serializer, Object principal) {
        return serializer.serialize(serializer.principalType().cast(principal));
    }

    private record Snapshot(String type, int version, List<SerializedPrincipal> principals) {
    }

    private record SerializedPrincipal(String realm, String type, byte[] data) {
    }
}
