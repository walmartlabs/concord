package com.walmartlabs.concord.server.process.event;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(as = ImmutableProcessEventEntry.class)
@JsonDeserialize(as = ImmutableProcessEventEntry.class)
public interface ProcessEventEntry extends Serializable {

    @Deprecated
    UUID id();

    long seqId();

    String eventType();

    @Nullable
    Map<String, Object> data();

    /**
     * should match the format in {@link com.walmartlabs.concord.server.OffsetDateTimeParam}
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    OffsetDateTime eventDate();

    static ImmutableProcessEventEntry.Builder builder() {
        return ImmutableProcessEventEntry.builder();
    }

    static ImmutableProcessEventEntry.Builder from(ProcessEventEntry e) {
        return ImmutableProcessEventEntry.builder().from(e);
    }
}
