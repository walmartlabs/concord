package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.*;

@Value.Immutable
@JsonInclude(Include.NON_EMPTY)
@JsonSerialize(as = ImmutableProcessEntry.class)
@JsonDeserialize(as = ImmutableProcessEntry.class)
public interface ProcessEntry extends Serializable {

    UUID instanceId();

    ProcessKind kind();

    @Nullable
    UUID parentInstanceId();

    @Nullable
    UUID orgId();

    @Nullable
    String orgName();

    @Nullable
    UUID projectId();

    @Nullable
    String projectName();

    @Nullable
    UUID repoId();

    @Nullable
    String repoName();

    @Nullable
    String repoUrl();

    @Nullable
    String repoPath();

    @Nullable
    String commitId();

    @Nullable
    String commitMsg();

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    Date createdAt();

    @Nullable
    String initiator();

    @Nullable
    UUID initiatorId();

    ProcessStatus status();

    @Nullable
    String lastAgentId();
    
    @Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    Date startAt();
    
    @Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    Date lastUpdatedAt();

    @Nullable
    String logFileName();

    @Nullable
    Set<String> tags();

    @Nullable
    Set<UUID> childrenIds();

    @Nullable
    Map<String, Object> meta();

    @Nullable
    Set<String> handlers();

    @Nullable
    Map<String, Object> requirements();

    @Value.Default
    default boolean disabled() {
        return false;
    }

    @Nullable
    List<ProcessCheckpointEntry> checkpoints();

    @Nullable
    List<ProcessStatusHistoryEntry> statusHistory();

    @Nullable
    TriggeredByEntry triggeredBy();

    @Nullable
    Long timeout();

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableProcessCheckpointEntry.class)
    @JsonDeserialize(as = ImmutableProcessCheckpointEntry.class)
    interface ProcessCheckpointEntry {

        UUID id();

        String name();

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        Date createdAt();
    }

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonSerialize(as = ImmutableProcessStatusHistoryPayload.class)
    @JsonDeserialize(as = ImmutableProcessStatusHistoryPayload.class)
    interface ProcessStatusHistoryPayload {

        @Nullable
        UUID checkpointId();
    }

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableProcessStatusHistoryEntry.class)
    @JsonDeserialize(as = ImmutableProcessStatusHistoryEntry.class)
    interface ProcessStatusHistoryEntry {

        UUID id();

        ProcessStatus status();

        @Nullable
        ProcessStatusHistoryPayload payload();

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        Date changeDate();
    }

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableProcessWaitHistoryEntry.class)
    @JsonDeserialize(as = ImmutableProcessWaitHistoryEntry.class)
    interface ProcessWaitHistoryEntry {

        UUID id();

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        Date eventDate();

        String type();

        @Nullable
        String reason();

        @JsonAnyGetter
        Map<String, Object> payload();

        static ImmutableProcessWaitHistoryEntry.Builder builder() {
            return ImmutableProcessWaitHistoryEntry.builder();
        }
    }
}
