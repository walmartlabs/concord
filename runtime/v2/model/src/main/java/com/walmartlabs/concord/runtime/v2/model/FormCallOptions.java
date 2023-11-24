package com.walmartlabs.concord.runtime.v2.model;

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
import com.walmartlabs.concord.runtime.v2.parser.StepOptions;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface FormCallOptions extends StepOptions {

    long serialVersionUID = 1L;

    @Value.Default
    default boolean isYield() {
        return false;
    }

    @Value.Default
    default boolean saveSubmittedBy() {
        return false;
    }

    // TODO types
    @Value.Default
    @AllowNulls
    default Map<String, Serializable> runAs() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Serializable> values() {
        return Collections.emptyMap();
    }

    @Value.Default
    default List<FormField> fields() {
        return Collections.emptyList();
    }

    /**
     * Support for expressions in {@code fields}.
     */
    @Nullable
    String fieldsExpression();

    /**
     * Support for expressions in {@code values}.
     */
    @Nullable
    String valuesExpression();

    /**
     * Support for expressions in {@code runAs}.
     */
    @Nullable
    String runAsExpression();

    static ImmutableFormCallOptions.Builder builder() {
        return ImmutableFormCallOptions.builder();
    }
}
