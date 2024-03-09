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
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TriggerInternalIdCalculator {

    @SuppressWarnings("UnstableApiUsage")
    public static String getId(String name, List<String> activeProfiles, Map<String, Object> arguments, Map<String, Object> conditions, Map<String, Object> cfg) {
        HashFunction hf = Hashing.sha256();
        return hf.newHasher()
                .putUnencodedChars(name)
                .putObject(ensureList(activeProfiles), (from, into) -> from.stream().sorted().forEach(p -> into.putString(p, StandardCharsets.UTF_8)))
                .putUnencodedChars(objectToString(arguments))
                .putUnencodedChars(objectToString(conditions))
                .putUnencodedChars(objectToString(cfg))
                .hash()
                .toString();
    }

    @SuppressWarnings("unchecked")
    private static String objectToString(Object o) {
        if (o == null) {
            return "";
        }

        if (o instanceof Collection) {
            return hashCollection((Collection<Object>) o);
        } else if (o instanceof Map) {
            return hashMap((Map<String, Object>) o);
        }

        return o.toString();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static String hashCollection(Collection<Object> collection) {
        Hasher hasher = Hashing.sha256().newHasher();
        collection.stream()
                .map(TriggerInternalIdCalculator::objectToString)
                .sorted()
                .forEach(hasher::putUnencodedChars);
        return hasher.hash().toString();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static String hashMap(Map<String, Object> map) {
        Hasher hasher = Hashing.sha256().newHasher();

        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> hasher.putUnencodedChars(e.getKey())
                        .putUnencodedChars(objectToString(e.getValue())));

        return hasher.hash().toString();
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
