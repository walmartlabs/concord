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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value.Immutable
@JsonInclude(Include.NON_EMPTY)
@JsonSerialize(as = ImmutableProcessEntry.class)
@JsonDeserialize(as = ImmutableProcessEntry.class)
public interface ProcessEntry extends Serializable {

    long serialVersionUID = 1L;

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
    String commitBranch();

    @Deprecated
    @Nullable
    String commitMsg();

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    OffsetDateTime createdAt();

    @Nullable
    String initiator();

    @Nullable
    UUID initiatorId();

    ProcessStatus status();

    @Nullable
    String lastAgentId();

    @Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    OffsetDateTime startAt();

    @Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    OffsetDateTime lastUpdatedAt();

    @Nullable
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    OffsetDateTime lastRunAt();

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
    List<CheckpointRestoreHistoryEntry> checkpointRestoreHistory();

    @Nullable
    List<ProcessStatusHistoryEntry> statusHistory();

    @Nullable
    TriggeredByEntry triggeredBy();

    @Nullable
    Long timeout();

    @Nullable
    Long suspendTimeout();

    @Nullable
    String runtime();

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableCheckpointRestoreHistoryEntry.class)
    @JsonDeserialize(as = ImmutableCheckpointRestoreHistoryEntry.class)
    interface CheckpointRestoreHistoryEntry extends Serializable{

        long serialVersionUID = 1L;

        long id();

        UUID checkpointId();

        ProcessStatus processStatus();

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        OffsetDateTime changeDate();
    }

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableProcessCheckpointEntry.class)
    @JsonDeserialize(as = ImmutableProcessCheckpointEntry.class)
    interface ProcessCheckpointEntry extends Serializable {

        long serialVersionUID = 1L;

        UUID id();

        String name();

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        OffsetDateTime createdAt();

        @Nullable
        UUID correlationId();
    }

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableProcessStatusHistoryEntry.class)
    @JsonDeserialize(as = ImmutableProcessStatusHistoryEntry.class)
    interface ProcessStatusHistoryEntry extends Serializable {

        long serialVersionUID = 1L;

        UUID id();

        ProcessStatus status();

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        OffsetDateTime changeDate();
    }

    @Value.Immutable
    @JsonInclude(Include.NON_EMPTY)
    @JsonSerialize(as = ImmutableProcessWaitEntry.class)
    @JsonDeserialize(as = ImmutableProcessWaitEntry.class)
    interface ProcessWaitEntry extends Serializable {

        long serialVersionUID = 1L;

        @Value.Parameter
        boolean isWaiting();

        @Nullable
        @Value.Parameter
        // Can't use AbstractWaitCondition because swagger can't generate code :(
        List<Map<String, Object>> waits();

        static ProcessWaitEntry of(boolean isWaiting, List<Map<String, Object>> waits) {
            return ImmutableProcessWaitEntry.of(isWaiting, waits);
        }
    }
}
