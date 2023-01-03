package com.walmartlabs.concord.process.loader.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface FormField {

    String name();

    @Nullable
    String label();

    String type();

    @Nullable
    Serializable defaultValue();

    @Nullable
    Serializable allowedValue();

    @Value.Default
    @AllowNulls
    default Map<String, Serializable> options() {
        return Collections.emptyMap();
    }

    @Nullable
    SourceMap location();

    static ImmutableFormField.Builder builder() {
        return ImmutableFormField.builder();
    }
}
