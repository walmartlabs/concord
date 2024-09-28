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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.io.Serial;
import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableDefaultExclusiveMode.class)
@JsonDeserialize(as = ImmutableDefaultExclusiveMode.class)
public interface ExclusiveMode extends Serializable {

    @Value.Parameter
    @JsonProperty(value = "group", required = true)
    String group();

    @Value.Parameter
    @Value.Default
    default Mode mode() {
        return Mode.cancel;
    }

    enum Mode {
        /**
         * Cancel the current process if there's already a running process with the same {@link #group()} value.
         */
        cancel,

        /**
         * Cancel all other processes (enqueued, waiting, running or suspended) with the same {@link #group()} value.
         */
        cancelOld,

        /**
         * Wait in the queue if there's already a running process with the same {@link #group()} value.
         */
        wait
    }

    static ExclusiveMode of(String group, Mode mode) {
        return ImmutableDefaultExclusiveMode.of(group, mode);
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableDefaultExclusiveMode.class)
    @JsonDeserialize(as = ImmutableDefaultExclusiveMode.class)
    interface DefaultExclusiveMode extends ExclusiveMode {

        @Serial
        long serialVersionUID = 1L;

    }

}
