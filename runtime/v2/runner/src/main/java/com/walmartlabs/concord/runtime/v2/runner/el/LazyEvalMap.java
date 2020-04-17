package com.walmartlabs.concord.runtime.v2.runner.el;

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

import com.walmartlabs.concord.runtime.v2.sdk.Context;

import java.util.*;
import java.util.stream.Collectors;

public class LazyEvalMap implements Map<Object, Object> {

    private final LazyExpressionEvaluator evaluator;

    private Context context;

    private final LinkedHashSet<Object> orderedKeys;
    private final Set<Object> inflightKeys = new HashSet<>();
    private final Map<Object, Object> originalValues;
    private final Map<Object, Object> evaluatedValues = new LinkedHashMap<>();

    public LazyEvalMap(LazyExpressionEvaluator evaluator, Map<Object, Object> nonEvaluatedItems) {
        this(evaluator, null, nonEvaluatedItems);
    }

    public LazyEvalMap(LazyExpressionEvaluator evaluator, Context context, Map<Object, Object> nonEvaluatedItems) {
        this.evaluator = evaluator;
        this.context = context;
        this.orderedKeys = new LinkedHashSet<>(nonEvaluatedItems.keySet());
        this.originalValues = nonEvaluatedItems;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    @Override
    public int size() {
        return orderedKeys.size();
    }

    @Override
    public boolean isEmpty() {
        return orderedKeys.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return orderedKeys.contains(key);
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get(Object key) {
        return evalValue(key);
    }

    @Override
    public Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<?, ?> m) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Object> keySet() {
        return orderedKeys;
    }

    @Override
    public Collection<Object> values() {
        return entrySet().stream().map(Entry::getValue).collect(Collectors.toList());
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        Set<Entry<Object, Object>> result = new LinkedHashSet<>();
        orderedKeys.forEach(k -> result.add(new LazyEntry(k)));
        return result;
    }

    private Object evalValue(Object key) {
        if (evaluatedValues.containsKey(key)) {
            return evaluatedValues.get(key);
        }

        if (!originalValues.containsKey(key)) {
            return null;
        }

        try {
            boolean newKey = inflightKeys.add(key);
            if (!newKey) {
                throw new RuntimeException("Key '" + key + "' already in evaluation");
            }

            Object originalValue = originalValues.get(key);
            Object evaluatedValue = evaluator.evalValue(context, originalValue, Object.class);

            evaluatedValues.put(key, evaluatedValue);

            return evaluatedValue;
        } finally {
            inflightKeys.remove(key);
        }
    }

    class LazyEntry implements Entry<Object, Object> {

        private final Object key;

        LazyEntry(Object key) {
            this.key = key;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return get(key);
        }

        @Override
        public Object setValue(Object value) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String toString() {
        return "LazyEvalMap{" +
                "orderedKeys=" + orderedKeys +
                ", inflightKeys=" + inflightKeys +
                ", originalValues=" + originalValues +
                ", evaluatedValues=" + evaluatedValues +
                '}';
    }
}
