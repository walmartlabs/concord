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

    /**
     * The runtime version to use.
     */
    @Value.Default
    default String runtime() {
        return "concord-v2";
    }

    /**
     * Global debug mode.
     */
    @Value.Default
    default boolean debug() {
        return false;
    }

    /**
     * List of active profiles.
     */
    @Value.Default
    default List<String> activeProfiles() {
        return Collections.emptyList();
    }

    /**
     * Name of the flow to start.
     */
    @Value.Default
    default String entryPoint() {
        return Constants.Request.DEFAULT_ENTRY_POINT_NAME;
    }

    /**
     * List of required dependencies.
     * May be a list of Maven coordinates (mvn://), URLs (https://), or local file paths (file://).
     */
    @Value.Default
    default List<String> dependencies() {
        return Collections.emptyList();
    }

    /**
     * List of extra dependencies. Extra dependencies are appended to the regular list of dependencies.
     * In profiles, the regular "dependencies" must be overridden entirely, "extraDependencies" can be used to add additional dependencies.
     */
    @Value.Default
    default List<String> extraDependencies() {
        return Collections.emptyList();
    }

    /**
     * Process arguments. Arbitrary key-value pairs.
     */
    @Value.Default
    @AllowNulls
    default Map<String, Object> arguments() {
        return Collections.emptyMap();
    }

    /**
     * Process metadata. Arbitrary key-value pairs.
     */
    @Value.Default
    default Map<String, Object> meta() {
        return Collections.emptyMap();
    }

    /**
     * Event configuration. Used to control the behavior of the event recording system.
     */
    @Value.Default
    default EventConfiguration events() {
        return EventConfiguration.builder().build();
    }

    /**
     * Process requirements. Used by the server to find a suitable agent.
     */
    @Value.Default
    default Map<String, Object> requirements() {
        return Collections.emptyMap();
    }

    /**
     * Process timeout. Limits the execution time of a process instance.
     */
    @Nullable
    Duration processTimeout();

    /**
     * Process suspend timeout. Limits the time a process instance can be suspended.
     */
    @Nullable
    Duration suspendTimeout();

    /**
     * Exclusive mode configuration.
     */
    @Nullable
    ExclusiveMode exclusive();

    /**
     * List of output variables.
     */
    @Value.Default
    default List<String> out() {
        return Collections.emptyList();
    }

    /**
     * Process template to use.
     */
    @Nullable
    String template();

    /**
     * Number of parallel threads to use in loops by default.
     */
    @Value.Default
    default int parallelLoopParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }

    static ImmutableProcessDefinitionConfiguration.Builder builder() {
        return ImmutableProcessDefinitionConfiguration.builder();
    }
}
