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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.common.ConfigurationUtils;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableEventConfiguration.class)
@JsonDeserialize(as = ImmutableEventConfiguration.class)
public interface EventConfiguration extends Serializable {

    long serialVersionUID = 1L;

    /**
     * Commonly used variable names that can potentially contain sensitive data.
     */
    Collection<String> DEFAULT_IN_VARS_BLACKLIST = Arrays.asList(
            "apiKey",
            "apiToken",
            "password",
            "privateKey",
            "vaultPassword");

    /**
     * Enable/disable recording of IN variables in task calls.
     */
    @Value.Default
    default boolean recordTaskInVars() {
        return false;
    }

    /**
     * Enable/disable truncating of IN variables in task call for events.
     */
    @Value.Default
    default boolean truncateInVars() {
        return true;
    }

    @Value.Default
    default int truncateMaxStringLength() {
        return 1024;
    }

    @Value.Default
    default int truncateMaxArrayLength() {
        return 32;
    }

    @Value.Default
    default int truncateMaxDepth() {
        return 10;
    }

    /**
     * Enable/disable recording of OUT variables in task calls.
     */
    @Value.Default
    default boolean recordTaskOutVars() {
        return false;
    }

    /**
     * Enable/disable truncating of OUT variables in task call for events.
     */
    @Value.Default
    default boolean truncateOutVars() {
        return true;
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

    static EventConfiguration merge(EventConfiguration a, EventConfiguration b) {
        return builder().from(a)
                .recordTaskInVars(a.recordTaskInVars() || b.recordTaskInVars())
                .truncateInVars(a.truncateInVars() || b.truncateInVars())
                .truncateMaxStringLength(Math.max(a.truncateMaxArrayLength(), b.truncateMaxArrayLength()))
                .truncateMaxArrayLength(Math.max(a.truncateMaxArrayLength(), b.truncateMaxArrayLength()))
                .truncateMaxDepth(Math.max(a.truncateMaxDepth(), b.truncateMaxDepth()))
                .recordTaskOutVars(a.recordTaskOutVars() || b.recordTaskInVars())
                .truncateOutVars(a.truncateOutVars() || b.truncateOutVars())
                .inVarsBlacklist(ConfigurationUtils.distinct(a.inVarsBlacklist(), b.inVarsBlacklist()))
                .outVarsBlacklist(ConfigurationUtils.distinct(a.outVarsBlacklist(), b.outVarsBlacklist()))
                .build();
    }
}
