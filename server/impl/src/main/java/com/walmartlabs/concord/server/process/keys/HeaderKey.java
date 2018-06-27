package com.walmartlabs.concord.server.process.keys;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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


import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class HeaderKey<T> extends Key<T> {

    private static final KeyIndex<HeaderKey<?>> index = new KeyIndex<>(HeaderKey::new);

    @SuppressWarnings("unchecked")
    public static <T> HeaderKey<T> register(String name, Class<T> type) {
        return (HeaderKey<T>) index.register(name, type);
    }

    @SuppressWarnings("unchecked")
    public static <T> HeaderKey<Collection<T>> registerCollection(String name) {
        return (HeaderKey<Collection<T>>) index.register(name, Collection.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> HeaderKey<Set<T>> registerSet(String name) {
        return (HeaderKey<Set<T>>) index.register(name, Set.class);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> HeaderKey<Map<K, V>> registerMap(String name) {
        return (HeaderKey<Map<K, V>>) index.register(name, Map.class);
    }

    private HeaderKey(String key, Class<T> type) {
        super(key, type);
    }
}
