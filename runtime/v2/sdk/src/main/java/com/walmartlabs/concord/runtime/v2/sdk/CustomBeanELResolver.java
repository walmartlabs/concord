package com.walmartlabs.concord.runtime.v2.sdk;

import org.immutables.value.Value;

import javax.annotation.Nullable;

public interface CustomBeanELResolver {

    Result invoke(Object base, String method, Object[] params);

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface Result {

        @Nullable
        Object value();

        @Nullable
        Object base();

        @Nullable
        String method();

        static Result of(Object value) {
            return ImmutableResult.builder()
                    .value(value)
                    .build();
        }

        static Result of(Object base, String method) {
            return ImmutableResult.builder()
                    .base(base)
                    .method(method)
                    .build();
        }
    }
}
