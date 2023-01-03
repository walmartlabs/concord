package com.walmartlabs.concord.runtime.v2.sdk;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(as = ImmutableProjectInfo.class)
@JsonDeserialize(as = ImmutableProjectInfo.class)
public interface ProjectInfo extends Serializable {

    long serialVersionUID = 1L;

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
    String repoBranch();

    @Nullable
    String repoPath();

    @Nullable
    String repoCommitId();

    @Nullable
    String repoCommitAuthor();

    @Nullable
    String repoCommitMessage();

    static ImmutableProjectInfo.Builder builder() {
        return ImmutableProjectInfo.builder();
    }
}
