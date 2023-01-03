package com.walmartlabs.concord.runtime.common.cfg;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableRunnerConfiguration.class)
@JsonDeserialize(as = ImmutableRunnerConfiguration.class)
@JsonIgnoreProperties(ignoreUnknown = true)
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
     * Enable or disable the Sisu index for Guice bindings.
     * Speeds up the startup, but requires the dependencies to use "sisu-maven-plugin".
     *
     * @apiNote for the runtime v2 this option is always {@code true}.
     */
    @Value.Default
    default boolean enableSisuIndex() {
        return false;
    }

    /**
     * Default logging level.
     *
     * @deprecated used only in the runtime v1.
     */
    @Value.Default
    @Deprecated
    default String logLevel() {
        return "INFO";
    }

    /**
     * Logging parameters used in the runtime.
     */
    @Value.Default
    default LoggingConfiguration logging() {
        return LoggingConfiguration.builder().build();
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
     * List of the process' dependencies (JAR file paths).
     */
    @Value.Default
    default Collection<String> dependencies() {
        return Collections.emptyList();
    }

    /**
     * Dependency Manager configuration.
     */
    @Value.Default
    default DependencyManagerConfiguration dependencyManager() {
        return DependencyManagerConfiguration.builder().build();
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
