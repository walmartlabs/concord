package com.walmartlabs.concord.runtime.v2.sdk;

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
import com.walmartlabs.concord.runtime.v2.model.EventConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableProcessConfiguration.class)
@JsonDeserialize(as = ImmutableProcessConfiguration.class)
public interface ProcessConfiguration extends Serializable {

    long serialVersionUID = 1L;

    @Nullable
    UUID instanceId();

    @Value.Default
    default boolean debug() {
        return false;
    }

    @Value.Default
    default boolean dryRunMode() {
        return false;
    }

    @Value.Default
    default String entryPoint() {
        return Constants.Request.DEFAULT_ENTRY_POINT_NAME;
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

    // TODO types
    @Nullable
    Map<String, Object> initiator();

    // TODO types
    @Nullable
    Map<String, Object> currentUser();

    @Value.Default
    default ProcessInfo processInfo() {
        return ProcessInfo.builder().build();
    }

    @Value.Default
    default ProjectInfo projectInfo() {
        return ProjectInfo.builder().build();
    }

    @Value.Default
    default EventConfiguration events() {
        return EventConfiguration.builder().build();
    }

    @Value.Default
    default Map<String, Map<String, Object>> defaultTaskVariables() {
        return Collections.emptyMap();
    }

    @Value.Default
    default List<String> out() {
        return Collections.emptyList();
    }

    static ImmutableProcessConfiguration.Builder builder() {
        return ImmutableProcessConfiguration.builder();
    }
}
