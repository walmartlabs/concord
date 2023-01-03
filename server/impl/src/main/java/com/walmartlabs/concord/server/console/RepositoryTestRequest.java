package com.walmartlabs.concord.server.console;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class RepositoryTestRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @ConcordKey
    private final String orgName;

    @ConcordKey
    private final String projectName;

    @NotNull
    private final String url;

    private final String branch;
    private final String commitId;
    private final String path;
    private final UUID secretId;

    @JsonCreator
    public RepositoryTestRequest(@JsonProperty("orgName") String orgName,
                                 @JsonProperty("projectName") String projectName,
                                 @JsonProperty("url") String url,
                                 @JsonProperty("branch") String branch,
                                 @JsonProperty("commitId") String commitId,
                                 @JsonProperty("path") String path,
                                 @JsonProperty("secretId") UUID secretId) {

        this.orgName = orgName;
        this.projectName = projectName;
        this.url = url;
        this.branch = branch;
        this.commitId = commitId;
        this.path = path;
        this.secretId = secretId;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getUrl() {
        return url;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getPath() {
        return path;
    }

    public UUID getSecretId() {
        return secretId;
    }
}
