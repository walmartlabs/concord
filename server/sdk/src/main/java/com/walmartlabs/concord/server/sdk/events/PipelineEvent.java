package com.walmartlabs.concord.server.sdk.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.immutables.value.Value;

import java.io.Serial;
import java.io.Serializable;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutablePipelineEvent.class)
@JsonDeserialize(as = ImmutablePipelineEvent.class)
public interface PipelineEvent extends Serializable {

    @Serial
    long serialVersionUID = 1L;

    ProcessKey processKey();

    EventType eventType();

    enum EventType {
        ENQUEUE_PROCESS_PIPELINE_START,
        ENQUEUE_PROCESS_PIPELINE_END
    }

    static ImmutablePipelineEvent.Builder builder() {
        return ImmutablePipelineEvent.builder();
    }
}
