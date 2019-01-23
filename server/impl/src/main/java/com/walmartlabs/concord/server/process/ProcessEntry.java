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
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    @JsonRawValue
    Object checkpoints();

    @Nullable
    @JsonRawValue
    Object history();
}
