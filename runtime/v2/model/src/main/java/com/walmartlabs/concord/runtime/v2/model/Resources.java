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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.List;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableResources.class)
@JsonDeserialize(as = ImmutableResources.class)
public interface Resources extends Serializable {

    List<String> DEFAULT_CONCORD_RESOURCES = List.of("glob:concord/{**/,}{*.,}concord.{yml,yaml}");
    List<String> DEFAULT_CONCORD_CLI_INCLUDES = List.of("glob:*", "glob:**/*");
    List<String> DEFAULT_CONCORD_CLI_EXCLUDES = List.of("glob:target/**");

    long serialVersionUID = 1L;

    /**
     * List of files (glob patterns, regexes or plain paths) that the runtime uses.
     * By default, the runtime loads the root concord.yaml or .yml file and all files in concord/ subdirectory
     * with the .concord.y?ml extension.
     */
    @Value.Default
    default List<String> concord() {
        return DEFAULT_CONCORD_RESOURCES;
    }

    /**
     * List of files that the CLI copies into the ${workDir} before starting the runtime.
     * By default, the CLI copies all files in the current directory except for the ./target/ directory.
     */
    @Value.Default
    default ConcordCliResources concordCli() {
        return ImmutableConcordCliResources.builder().build();
    }

    static ImmutableResources.Builder builder() {
        return ImmutableResources.builder();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonSerialize(as = ImmutableConcordCliResources.class)
    @JsonDeserialize(as = ImmutableConcordCliResources.class)
    interface ConcordCliResources extends Serializable {

        /**
         * Include patterns.
         */
        @Value.Default
        default List<String> includes() {
            return DEFAULT_CONCORD_CLI_INCLUDES;
        }

        /**
         * Exclude patterns.
         */
        @Value.Default
        default List<String> excludes() {
            return DEFAULT_CONCORD_CLI_EXCLUDES;
        }
    }
}
