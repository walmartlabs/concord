package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Set;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableRuntimeRule.class)
@JsonDeserialize(as = ImmutableRuntimeRule.class)
public interface RuntimeRule extends Serializable {

    long serialVersionUID = 1L;

    @Nullable
    @Value.Parameter
    String msg();

    @Value.Parameter
    @JsonProperty("runtimes")
    Set<String> allowedRuntimes();

    @Nullable
    @Value.Parameter
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate projectCreatedAfter();

    static RuntimeRule of(String msg, Set<String> runtimes, LocalDate projectCreatedAfter) {
        return ImmutableRuntimeRule.of(msg, runtimes, projectCreatedAfter);
    }
}
