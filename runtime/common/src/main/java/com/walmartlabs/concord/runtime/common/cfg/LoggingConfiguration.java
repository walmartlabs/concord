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

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableLoggingConfiguration.class)
@JsonDeserialize(as = ImmutableLoggingConfiguration.class)
public interface LoggingConfiguration {

    @Value.Default
    default boolean segmentedLogs() {
        return true;
    }

    /**
     * If {@code true} {@code System.out} and {@code System.err} will be
     * redirected into SLF4J and, subsequently, into correct log segments.
     * <p/>
     * Default is {@code true}.
     * <p/>
     * Requires {@link #segmentedLogs()}.
     *
     * @apiNote only for the runtime v2. Not applicable for the CLI version
     * of the runner.
     */
    @Value.Default
    default boolean sendSystemOutAndErrToSLF4J() {
        return true;
    }

    /**
     * If {@code true}, any ${workDir} value will be replaced with literal
     * "$WORK_DIR" string. Reduces noise in the logs.
     */
    @Value.Default
    default boolean workDirMasking() {
        return true;
    }

    static ImmutableLoggingConfiguration.Builder builder() {
        return ImmutableLoggingConfiguration.builder();
    }
}
