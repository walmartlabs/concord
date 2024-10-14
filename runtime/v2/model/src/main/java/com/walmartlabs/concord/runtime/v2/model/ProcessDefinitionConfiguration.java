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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.AllowNulls;
import com.walmartlabs.concord.sdk.Constants;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableProcessDefinitionConfiguration.class)
@JsonDeserialize(as = ImmutableProcessDefinitionConfiguration.class)
public interface ProcessDefinitionConfiguration extends Serializable {

    long serialVersionUID = 1L;

    @Value.Default
    default String runtime() {
        return "concord-v2";
    }

    @Value.Default
    default boolean debug() {
        return false;
    }

    @Value.Default
    default List<String> activeProfiles() {
        return Collections.emptyList();
    }

    @Value.Default
    default String entryPoint() {
        return Constants.Request.DEFAULT_ENTRY_POINT_NAME;
    }

    @Value.Default
    default List<String> dependencies() {
        return Collections.emptyList();
    }

    @Value.Default
    @AllowNulls
    default Map<String, Object> arguments() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> meta() {
        return Collections.emptyMap();
    }

    @Value.Default
    default EventConfiguration events() {
        return EventConfiguration.builder().build();
    }

    @Value.Default
    default Map<String, Object> requirements() {
        return Collections.emptyMap();
    }

    @Nullable
    Duration processTimeout();

    @Nullable
    Duration suspendTimeout();

    @Nullable
    ExclusiveMode exclusive();

    @Value.Default
    default List<String> out() {
        return Collections.emptyList();
    }

    @Nullable
    String template();

    @Value.Default
    default int parallelLoopParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }

    static ImmutableProcessDefinitionConfiguration.Builder builder() {
        return ImmutableProcessDefinitionConfiguration.builder();
    }
}
