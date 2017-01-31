package com.walmartlabs.concord.server.process.keys;

import java.io.Serializable;

public abstract class Key<T> implements Serializable {

    private final String name;
    private final Class<T> type;

    protected Key(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public T cast(Object v) {
        if (v == null) {
            return null;
        }

        Class<?> other = v.getClass();
        if (!type.isAssignableFrom(other)) {
            throw new IllegalArgumentException("Invalid value type: expected " + type + ", got " + other);
        }

        return type.cast(v);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Key key1 = (Key) o;
        return name.equals(key1.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName() + " [" + name + "]";
    }
}
