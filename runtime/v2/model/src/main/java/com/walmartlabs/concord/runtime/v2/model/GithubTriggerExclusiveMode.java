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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableGithubTriggerExclusiveMode.class)
@JsonDeserialize(as = ImmutableGithubTriggerExclusiveMode.class)
public interface GithubTriggerExclusiveMode extends Serializable {

    long serialVersionUID = 1L;

    @Nullable
    @Value.Parameter
    @JsonProperty(value = "group")
    String group();

    @Nullable
    @Value.Parameter
    @JsonProperty(value = "groupBy")
    GroupBy groupBy();

    enum GroupBy {
        branch
    }

    @Value.Parameter
    @Value.Default
    default ExclusiveMode.Mode mode() {
        return ExclusiveMode.Mode.wait;
    }
}
