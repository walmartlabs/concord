package com.walmartlabs.concord.server.process.keys;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @param <K> type of a key
 */
public final class KeyIndex<K extends Key<?>> implements Serializable {

    private final Map<String, K> keys = new HashMap<>();
    private final BiFunction<String, Class<?>, K> keyMaker;

    public KeyIndex(BiFunction<String, Class<?>, K> keyMaker) {
        this.keyMaker = keyMaker;
    }

    public K register(String name, Class<?> type) {
        synchronized (keys) {
            if (keys.containsKey(name)) {
                throw new IllegalStateException("Key '" + name + "' is already registered. " +
                        "Check for duplicate declarations in the code");
            }

            K k = keyMaker.apply(name, type);
            keys.put(name, k);
            return k;
        }
    }

    public K get(String name) {
        synchronized (keys) {
            return keys.get(name);
        }
    }
}
