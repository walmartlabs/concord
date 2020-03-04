package com.walmartlabs.concord.runtime.v2.model;

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

import com.fasterxml.jackson.core.JsonLocation;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Value.Immutable
public interface Forms extends Serializable  {

    long serialVersionUID = 1L;

    @Value.Default
    default List<Form> items() {
        return Collections.emptyList();
    }

    @Nullable
    JsonLocation location();

    default boolean isEmpty() {
        return items().isEmpty();
    }

    default int size() {
        return items().size();
    }

    default Form get(String formName) {
        return items().stream()
                .filter(i -> formName.equals(i.name()))
                .findFirst()
                .orElse(null);
    }

    static Forms of(List<Form> items, JsonLocation location) {
        return builder().addAllItems(items).location(location).build();
    }

    static ImmutableForms.Builder builder() {
        return ImmutableForms.builder();
    }

    static Forms merge(Forms a, Forms b) {
        List<Form> result = new ArrayList<>();
        result.addAll(a.items());
        result.addAll(b.items());
        return Forms.of(result, null);
    }
}
