package com.walmartlabs.concord.server.queueclient.message;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.imports.Imports;

import java.util.UUID;

public class ProcessResponse extends Message {

    private final String sessionToken;
    private final UUID processId;
    private final String orgName; // TODO rename to secretOrgName
    private final String repoUrl;
    private final String repoPath;
    private final String commitId;
    private final String secretName;
    private final Imports imports;

    @JsonCreator
    public ProcessResponse(
            @JsonProperty("correlationId") long correlationId,
            @JsonProperty("sessionToken") String sessionToken,
            @JsonProperty("processId") UUID processId,
            @JsonProperty("orgName") String orgName,
            @JsonProperty("repoUrl") String repoUrl,
            @JsonProperty("repoPath") String repoPath,
            @JsonProperty("commitId") String commitId,
            @JsonProperty("secretName") String secretName,
            @JsonProperty("imports") Imports imports) {

        super(MessageType.PROCESS_RESPONSE);

        setCorrelationId(correlationId);
        this.sessionToken = sessionToken;
        this.processId = processId;
        this.orgName = orgName;
        this.repoUrl = repoUrl;
        this.repoPath = repoPath;
        this.commitId = commitId;
        this.secretName = secretName;
        this.imports = imports;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public UUID getProcessId() {
        return processId;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getSecretName() {
        return secretName;
    }

    public Imports getImports() {
        return imports;
    }

    @Override
    public String toString() {
        return "ProcessResponse{" +
                "sessionToken='" + sessionToken + '\'' +
                ", processId=" + processId +
                ", orgName='" + orgName + '\'' +
                ", repoUrl='" + repoUrl + '\'' +
                ", repoPath='" + repoPath + '\'' +
                ", commitId='" + commitId + '\'' +
                ", secretName='" + secretName + '\'' +
                ", imports=" + imports +
                '}';
    }
}
