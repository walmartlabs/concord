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


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class TriggerInternalIdCalculator {

    public static String getId(String name, List<String> activeProfiles, Map<String, Object> arguments, Map<String, Object> conditions, Map<String, Object> cfg) {
        MessageDigest md = sha256();
        putUnencodedChars(md, name);
        ensureList(activeProfiles).stream()
                .sorted()
                .forEach(p -> md.update(p.getBytes(StandardCharsets.UTF_8)));
        putUnencodedChars(md, objectToString(arguments));
        putUnencodedChars(md, objectToString(conditions));
        putUnencodedChars(md, objectToString(cfg));
        return HexFormat.of().formatHex(md.digest());
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

    private static String hashCollection(Collection<Object> collection) {
        MessageDigest md = sha256();
        collection.stream()
                .map(TriggerInternalIdCalculator::objectToString)
                .sorted()
                .forEach(s -> putUnencodedChars(md, s));
        return HexFormat.of().formatHex(md.digest());
    }

    private static String hashMap(Map<String, Object> map) {
        MessageDigest md = sha256();

        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    putUnencodedChars(md, e.getKey());
                    putUnencodedChars(md, objectToString(e.getValue()));
                });

        return HexFormat.of().formatHex(md.digest());
    }

    /**
     * Feeds each UTF-16 code unit as two little-endian bytes, without any charset encoding,
     * so unpaired surrogates are hashed as-is. Keeps the IDs produced by the former
     * Guava-based implementation ({@code Hasher#putUnencodedChars}).
     */
    private static void putUnencodedChars(MessageDigest md, String s) {
        byte[] bytes = new byte[s.length() * 2];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            bytes[i * 2] = (byte) c;
            bytes[i * 2 + 1] = (byte) (c >>> 8);
        }
        md.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
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
