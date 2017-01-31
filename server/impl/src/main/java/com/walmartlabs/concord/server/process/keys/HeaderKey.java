package com.walmartlabs.concord.server.process.keys;

public class HeaderKey<T> extends Key<T> {

    private static final KeyIndex<HeaderKey<?>> index = new KeyIndex<>(HeaderKey::new);

    @SuppressWarnings("unchecked")
    public static <T> HeaderKey<T> register(String name, Class<T> type) {
        return (HeaderKey<T>) index.register(name, type);
    }

    private HeaderKey(String key, Class<T> type) {
        super(key, type);
    }
}
