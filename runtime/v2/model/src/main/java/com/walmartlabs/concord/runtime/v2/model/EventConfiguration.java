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
     * Enable/disable recording of process events.
     */
    @Value.Default
    default boolean recordEvents() {
        return true;
    }

    /**
     * Maximum number of process events to report per batch.
     */
    @Value.Default
    default int batchSize() {
        return 1;
    }

    /**
     * Interval, in seconds after which any queued process events will be reported.
     * <p>
     * Typically, batched events are reported on process termination or when
     * the queued number equals {@link #batchSize()}. A long-running task call
     * holds up event recording if a scheduled flush is not performed.
     */
    @Value.Default
    default int batchFlushInterval() {
        return 15;
    }

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

    /**
     * Maximum allowed length of string values.
     * The runtime truncates strings larger than {@link #truncateMaxStringLength()}.
     *
     * @return
     */
    @Value.Default
    default int truncateMaxStringLength() {
        return 1024;
    }

    /**
     * Maximum allowed length of array (list) values.
     * The runtime truncates arrays larger than {@link #truncateMaxArrayLength()}.
     *
     * @return
     */
    @Value.Default
    default int truncateMaxArrayLength() {
        return 32;
    }

    /**
     * Maximum allowed depth of nested values.
     * The runtime truncates references deeper than {@link #truncateMaxDepth()}.
     *
     * @return
     */
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

    @Value.Default
    default boolean updateMetaOnAllEvents() {
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

    /**
     * Enable/disable recording of metadata in task calls.
     */
    @Value.Default
    default boolean recordTaskMeta() {
        return false;
    }

    /**
     * Enable/disable truncating of metadata in task call for events.
     */
    @Value.Default
    default boolean truncateMeta() {
        return true;
    }

    /**
     * metadata in the blacklist won't be recorded.
     */
    @Value.Default
    default Collection<String> metaBlacklist() {
        return Collections.emptyList();
    }
    
    static ImmutableEventConfiguration.Builder builder() {
        return ImmutableEventConfiguration.builder();
    }
}
