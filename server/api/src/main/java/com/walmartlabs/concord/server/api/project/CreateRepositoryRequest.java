package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;

public class CreateRepositoryRequest extends UpdateRepositoryRequest {

    @NotNull
    @ConcordKey
    private final String name;

    public CreateRepositoryRequest(@JsonProperty("name") String name,
                                   @JsonProperty("url") String url,
                                   @JsonProperty("branch") String branch,
                                   @JsonProperty("commitId") String commitId,
                                   @JsonProperty("secret") String secret) {

        super(url, branch, commitId, secret);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "CreateRepositoryRequest{" +
                "name='" + name + '\'' +
                ", url='" + getUrl() + '\'' +
                ", branch='" + getBranch() + '\'' +
                ", commitId='" + getCommitId() + '\'' +
                ", secret='" + getSecret() + '\'' +
                '}';
    }
}
