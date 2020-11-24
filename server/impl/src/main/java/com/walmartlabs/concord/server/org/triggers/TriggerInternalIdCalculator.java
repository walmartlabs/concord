package com.walmartlabs.concord.server.org.triggers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class TriggerInternalIdCalculator {

    @SuppressWarnings("UnstableApiUsage")
    public static String getId(String name, List<String> activeProfiles, Map<String, Object> arguments, Map<String, Object> conditions, Map<String, Object> cfg) {
        HashFunction hf = Hashing.sha256();
        return hf.newHasher()
                .putString(name, StandardCharsets.UTF_8)
                .putObject(ensureList(activeProfiles), (from, into) -> from.stream().sorted().forEach(p -> into.putString(p, StandardCharsets.UTF_8)))
                .putBytes(serialize(toSortedMap(arguments)))
                .putBytes(serialize(toSortedMap(conditions)))
                .putBytes(serialize(toSortedMap(cfg)))
                .hash()
                .toString();
    }

    @SuppressWarnings("unchecked")
    private static Object toSorted(Object o) {
        if (o instanceof Collection) {
            return toSortedCollection((Collection<Object>) o);
        } else if (o instanceof Map) {
            return toSortedMap((Map<String, Object>) o);
        }
        return o;
    }

    private static Collection<Object> toSortedCollection(Collection<Object> collection) {
        if (collection == null) {
            return Collections.emptyList();
        }

        List<Object> result = new ArrayList<>();
        collection.stream().sorted().forEach(c -> result.add(toSorted(c)));
        return result;
    }

    // use LinkedHashMap instead of Map, because LinkedHashMap is serializable
    private static LinkedHashMap<String, Object> toSortedMap(Map<String, Object> map) {
        if (map == null) {
            return new LinkedHashMap<>();
        }

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> result.put(e.getKey(), toSorted(e.getValue())));

        return result;
    }

    private static byte[] serialize(LinkedHashMap<String, Object> map) {
        if (map == null) {
            return new byte[0];
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {

            out.writeObject(map);
            out.flush();

            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <E> List<E> ensureList(List<E> list) {
        if (list != null) {
            return list;
        }
        return Collections.emptyList();
    }

    private TriggerInternalIdCalculator() {
    }
}
