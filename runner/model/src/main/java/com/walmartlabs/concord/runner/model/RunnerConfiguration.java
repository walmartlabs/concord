package com.walmartlabs.concord.runner.model;

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
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableRunnerConfiguration.class)
@JsonDeserialize(as = ImmutableRunnerConfiguration.class)
public interface RunnerConfiguration {

    /**
     * ID of the Agent which executes the process.
     */
    @Nullable
    String agentId();

    /**
     * Print out additional debugging information.
     */
    @Value.Default
    default boolean debug() {
        return false;
    }

    /**
     * Default logging level.
     */
    @Value.Default
    default String logLevel() {
        return "INFO";
    }

    /**
     * Java Security Manager configuration.
     */
    @Value.Default
    default SecurityManagerConfiguration securityManager() {
        return SecurityManagerConfiguration.builder().build();
    }

    /**
     * Concord API client configuration.
     */
    @Value.Default
    default ApiConfiguration api() {
        return ApiConfiguration.builder().build();
    }

    /**
     * List of dependencies (JAR file paths).
     */
    @Value.Default
    default Collection<String> dependencies() {
        return Collections.emptyList();
    }

    /**
     * Docker configuration for the process' containers.
     */
    @Value.Default
    default DockerConfiguration docker() {
        return DockerConfiguration.builder().build();
    }

    static ImmutableRunnerConfiguration.Builder builder() {
        return ImmutableRunnerConfiguration.builder();
    }
}
