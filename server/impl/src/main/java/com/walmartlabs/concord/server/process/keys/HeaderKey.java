package com.walmartlabs.concord.server.process.keys;

import java.util.Collection;
import java.util.Set;

public class HeaderKey<T> extends Key<T> {

    private static final KeyIndex<HeaderKey<?>> index = new KeyIndex<>(HeaderKey::new);

    @SuppressWarnings("unchecked")
    public static <T> HeaderKey<T> register(String name, Class<T> type) {
        return (HeaderKey<T>) index.register(name, type);
    }

    public static <T> HeaderKey<Collection<T>> registerCollection(String name) {
        return (HeaderKey<Collection<T>>) index.register(name, Collection.class);
    }

    public static <T> HeaderKey<Set<T>> registerSet(String name) {
        return (HeaderKey<Set<T>>) index.register(name, Set.class);
    }

    private HeaderKey(String key, Class<T> type) {
        super(key, type);
    }
}
