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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.imports.Imports;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableProcessDefinition.class)
@JsonDeserialize(as = ImmutableProcessDefinition.class)
public interface ProcessDefinition extends Serializable {

    long serialVersionUID = 1L;

    @Value.Default
    default String runtime() {
        return "concord-v2"; // TODO constants
    }

    // resources

    @Value.Default
    default ProcessConfiguration configuration() {
        return ProcessConfiguration.builder().build();
    }

    @Value.Default
    default Map<String, List<Step>> flows() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Profile> profiles() {
        return Collections.emptyMap();
    }

    default List<Trigger> triggers() {
        return Collections.emptyList();
    }

    @Value.Default
    default Imports imports() {
        return Imports.builder().build();
    }

    @Value.Default
    default Forms forms() {
        return Forms.builder().build();
    }
}
