package com.walmartlabs.concord.project.yaml;

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

import java.util.Map;

public class KV<K, V> implements Map.Entry<K, V> {

    private final K k;
    private final V v;

    public KV(K k, V v) {
        this.k = k;
        this.v = v;
    }

    @Override
    public K getKey() {
        return k;
    }

    @Override
    public V getValue() {
        return v;
    }

    @Override
    public V setValue(V value) {
        throw new IllegalStateException("Not allowed");
    }

    @Override
    public String toString() {
        return "KV{" +
                "k=" + k +
                ", v=" + v +
                '}';
    }
}
