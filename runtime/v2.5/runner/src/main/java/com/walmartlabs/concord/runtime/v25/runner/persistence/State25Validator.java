package com.walmartlabs.concord.runtime.v25.runner.persistence;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

final class State25Validator {

    private static final int MAX_DEPTH = 128;

    static void validate(State25 state) {
        visit(state, "$", new IdentityHashMap<>(), 0);
    }

    private static void visit(Object value, String path, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("State exceeds the maximum depth of " + MAX_DEPTH + " limit");
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?>) {
            return;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw unsupported(path + ".<key>", null);
                }
                visit(entry.getKey(), path + ".<key>", seen, depth + 1);
                visit(entry.getValue(), path + "." + entry.getKey(), seen, depth + 1);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            var i = 0;
            for (var item : collection) {
                visit(item, path + "[" + i++ + "]", seen, depth + 1);
            }
            return;
        }
        if (value.getClass().isArray()) {
            for (var i = 0; i < Array.getLength(value); i++) {
                visit(Array.get(value, i), path + "[" + i + "]", seen, depth + 1);
            }
            return;
        }
        if (value.getClass().isRecord() && value.getClass().getEnclosingClass() == State25.class
                || value instanceof State25) {
            for (var component : value.getClass().getRecordComponents()) {
                try {
                    visit(component.getAccessor().invoke(value), path + "." + component.getName(), seen, depth + 1);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Cannot inspect durable state at " + path, e);
                }
            }
            return;
        }
        if (value instanceof Serializable serializable) {
            verifySerializable(path, serializable);
            return;
        }
        throw unsupported(path, value);
    }

    private static IllegalArgumentException unsupported(String path, Object value) {
        return new IllegalArgumentException("State contains a non-durable value at " + path + ": "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static void verifySerializable(String path, Serializable value) {
        try (var buffer = new ByteArrayOutputStream(); var output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("State contains a non-durable value at " + path + ": "
                    + value.getClass().getName() + " cannot be serialized: " + e.getMessage(), e);
        }
    }

    private State25Validator() {
    }
}
