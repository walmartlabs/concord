package com.walmartlabs.concord.runtime.common.cfg;

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

import java.util.Collections;
import java.util.List;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableDockerConfiguration.class)
@JsonDeserialize(as = ImmutableDockerConfiguration.class)
public interface DockerConfiguration {

    /**
     * List of additional volumes which will be mounted to the process' containers.
     */
    @Value.Default
    default List<String> extraVolumes() {
        return Collections.emptyList();
    }

    /**
     * Expose docker daemon to containers created by DockerService
     */
    @Value.Default
    default boolean exposeDockerDaemon() {
        return true;
    }

    static ImmutableDockerConfiguration.Builder builder() {
        return ImmutableDockerConfiguration.builder();
    }
}
