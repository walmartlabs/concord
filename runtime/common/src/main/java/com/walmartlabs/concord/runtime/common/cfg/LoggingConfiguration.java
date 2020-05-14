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

import javax.annotation.Nullable;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableLoggingConfiguration.class)
@JsonDeserialize(as = ImmutableLoggingConfiguration.class)
public interface LoggingConfiguration {

    /**
     * Absolute path to the directory with segmented logs.
     * If {@code null} then the runtime is not going to use segmented
     * log files and logs will be redirected to stdout.
     * @apiNote only for the runtime v2.
     */
    @Nullable
    String segmentedLogDir();

    static ImmutableLoggingConfiguration.Builder builder() {
        return ImmutableLoggingConfiguration.builder();
    }
}
