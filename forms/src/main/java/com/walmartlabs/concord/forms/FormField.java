package com.walmartlabs.concord.forms;

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

import com.walmartlabs.concord.common.AllowNulls;
import org.immutables.serial.Serial;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
@Serial.Version(-9087815156857537835L) // for backward compatibility (java8 concord 1.92.0 version)
public interface FormField extends Serializable {

    String name();

    @Nullable
    String label();

    String type();

    Cardinality cardinality();

    @Nullable
    Serializable defaultValue();

    @Nullable
    Serializable allowedValue();

    @Value.Default
    @AllowNulls
    default Map<String, Serializable> options() {
        return Collections.emptyMap();
    }

    static ImmutableFormField.Builder builder() {
        return ImmutableFormField.builder();
    }

    default <T> T getOption(Option<T> option) {
        Object v = options().get(option.name());
        if (v == null) {
            return null;
        }

        return option.cast(v);
    }

    enum Cardinality {

        ONE_OR_NONE,
        ONE_AND_ONLY_ONE,
        AT_LEAST_ONE,
        ANY
    }

    @Value.Immutable
    interface Option<T> {

        @Value.Parameter
        String name();

        @Value.Parameter
        Class<T> type();

        static <T> Option<T> of(String name, Class<T> type) {
            return ImmutableOption.of(name, type);
        }

        default T cast(Object v) {
            if (v == null) {
                return null;
            }

            Class<?> other = v.getClass();
            if (!type().isAssignableFrom(other)) {
                throw new IllegalArgumentException("Invalid value type: expected " + type() + ", got " + other);
            }

            return type().cast(v);
        }
    }
}
