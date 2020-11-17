package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.model.ImmutableTaskCallOptions;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface StepMeta extends Serializable {

    @AllowNulls
    @Value.Default
    default Map<String, Serializable> items() {
        return Collections.emptyMap();
    }

    static ImmutableStepMeta.Builder builder() {
        return ImmutableStepMeta.builder();
    }

    static StepMeta of(Map<String, Serializable> items) {
        return builder()
                .putAllItems(items)
                .build();
    }

    @SuppressWarnings("unchecked")
    default <T extends Serializable> T get(String key) {
        return (T) items().get(key);
    }

    default String logLevel() {
        return getString(Constants.LOG_LEVEL);
    }

    default String segmentName() {
        return getString(Constants.SEGMENT_NAME);
    }

    default String getString(String key) {
        Serializable v = get(key);
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return (String) v;
        }

        throw new IllegalArgumentException("Invalid meta '" + key + "' type, expected: string, got: " + v.getClass());
    }
}
