package com.walmartlabs.concord.imports;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A collection of {@link Import}s.
 * Necessary for correct serialization of {@code items} of different types.
 */
@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableImports.class)
@JsonDeserialize(as = ImmutableImports.class)
public interface Imports extends Serializable  {

    long serialVersionUID = 1L;

    @Value.Default
    default List<Import> items() {
        return Collections.emptyList();
    }

    @JsonIgnore
    default boolean isEmpty() {
        return items().isEmpty();
    }

    static Imports of(List<Import> items) {
        return builder().items(items).build();
    }

    static ImmutableImports.Builder builder() {
        return ImmutableImports.builder();
    }

    static Imports merge(Imports a, Imports b) {
        List<Import> result = new ArrayList<>();
        result.addAll(a.items());
        result.addAll(b.items());
        return Imports.of(result);
    }
}
