package com.walmartlabs.concord.server.console;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryTestRequest implements Serializable {

    private final String url;
    private final String branch;
    private final String commitId;
    private final String secret;

    @JsonCreator
    public RepositoryTestRequest(@JsonProperty("url") String url,
                                 @JsonProperty("branch") String branch,
                                 @JsonProperty("commitId") String commitId,
                                 @JsonProperty("secret") String secret) {
        this.url = url;
        this.branch = branch;
        this.commitId = commitId;
        this.secret = secret;
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

    public String getSecret() {
        return secret;
    }
}
