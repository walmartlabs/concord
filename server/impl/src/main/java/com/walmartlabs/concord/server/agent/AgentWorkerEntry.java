package com.walmartlabs.concord.server.agent;

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
import java.util.Map;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableAgentWorkerEntry.class)
@JsonDeserialize(as = ImmutableAgentWorkerEntry.class)
public interface AgentWorkerEntry {

    String channelId();

    @Nullable // backward-compatibility with old agent versions
    String agentId();

    String userAgent();

    @Nullable
    Map<String, Object> capabilities();

    static ImmutableAgentWorkerEntry.Builder builder() {
        return ImmutableAgentWorkerEntry.builder();
    }
}
