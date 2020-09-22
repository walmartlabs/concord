package com.walmartlabs.concord.runtime.v2.serializer;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public final class SerializerUtils {

    public static <K, V> void writeNotEmptyObjectField(String fieldName, Map<K, V> value, JsonGenerator gen) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }

        gen.writeObjectField(fieldName, value);
    }

    public static <T> void writeNotEmptyObjectField(String fieldName, Collection<T> value, JsonGenerator gen) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }

        gen.writeObjectField(fieldName, value);
    }

    private SerializerUtils() {
    }
}
