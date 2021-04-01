package com.walmartlabs.concord.server.process.event;

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

import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value.Immutable
public interface ProcessEventFilter {

    ProcessKey processKey();

    @Nullable
    Long fromId();

    @Nullable
    OffsetDateTime after();

    @Nullable
    String eventType();

    @Nullable
    UUID eventCorrelationId();

    @Nullable
    EventPhase eventPhase();

    @Value.Default
    default int limit() {
        return -1;
    }

    @Value.Default
    default int offset() {
        return -1;
    }

    static ImmutableProcessEventFilter.Builder builder() {
        return ImmutableProcessEventFilter.builder();
    }
}
