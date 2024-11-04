package com.walmartlabs.concord.runtime.v2.sdk;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Objects;

public interface CustomBeanELResolver {

    /**
     * @return
     *  `null` if resolver do not know how to resolve base.method
     *  `Result.value` if resolver resolved value (value maybe null)
     *  `Result.base` and `Result.method` if resolver want's to override base or method
     */
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
                    .base(Objects.requireNonNull(base))
                    .method(Objects.requireNonNull(method))
                    .build();
        }
    }
}
