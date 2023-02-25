package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import java.util.*;
import java.util.stream.Collectors;

public final class LoopItemSanitizer {

    public static ArrayList<Serializable> sanitize(Object items) {
        if (items == null) {
            return null;
        }

        ArrayList<Serializable> result = toArray(items);
        if (result == null) {
            return null;
        }

        result.forEach(LoopItemSanitizer::assertItem);

        return result;
    }

    public static ArrayList<Serializable> toArray(Object items) {
        if (items instanceof Collection) {
            Collection<?> collection = (Collection<?>) items;
            if (collection.isEmpty()) {
                return null;
            }
            return collection.stream()
                    .map(LoopItemSanitizer::sanitizeItem)
                    .collect(Collectors.toCollection(ArrayList::new));
        } else if (items instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) items;
            return m.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else if (items.getClass().isArray()) {
            return Arrays.stream((Object[])items)
                    .map(LoopItemSanitizer::sanitizeItem)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        throw new IllegalArgumentException("'loop' accepts only Lists of items, Java Maps or arrays of values. Got: " + items.getClass());
    }

    private static Serializable sanitizeItem(Object item) {
        if (item == null) {
            return null;
        }

        if (item instanceof Serializable) {
            return (Serializable) item;
        }

        if (item instanceof Map.Entry) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) item;
            return new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue());
        } else if (item instanceof Map) {
            return new HashMap<>((Map<?, ?>)item);
        } else if (item instanceof Collection) {
            return new ArrayList<>((Collection<?>) item);
        }

        throw new IllegalArgumentException("Can't use non-serializable values in 'loop': " + item + " (" + item.getClass() + ")");
    }

    static void assertItem(Object item) {
        if (item == null) {
            return;
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())) {
            oos.writeObject(item);
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't use non-serializable values in 'loop': " + item + " (" + item.getClass() + ")");
        }
    }

    private LoopItemSanitizer() {
    }
}
