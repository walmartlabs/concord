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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableEventConfiguration.class)
@JsonDeserialize(as = ImmutableEventConfiguration.class)
public interface EventConfiguration {

    /**
     * Commonly used variable names that can potentially contain sensitive data.
     */
    Collection<String> DEFAULT_IN_VARS_BLACKLIST = Arrays.asList("password", "apiToken", "apiKey");

    /**
     * Enable/disable recording of IN variables in task calls.
     */
    @Value.Default
    default boolean recordTaskInVars() {
        return false;
    }

    /**
     * Enable/disable recording of OUT variables in task calls.
     */
    @Value.Default
    default boolean recordTaskOutVars() {
        return false;
    }

    /**
     * IN variables in the blacklist won't be recorded.
     */
    @Value.Default
    default Collection<String> inVarsBlacklist() {
        return DEFAULT_IN_VARS_BLACKLIST;
    }

    /**
     * OUT variables in the blacklist won't be recorded.
     */
    @Value.Default
    default Collection<String> outVarsBlacklist() {
        return Collections.emptyList();
    }

    static ImmutableEventConfiguration.Builder builder() {
        return ImmutableEventConfiguration.builder();
    }
}
