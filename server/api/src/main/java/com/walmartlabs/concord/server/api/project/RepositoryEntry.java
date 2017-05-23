package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositoryEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    @Size(max = 2048)
    private final String url;

    @Size(max = 255)
    private final String branch;

    @Size(max = 64)
    private final String commitId;

    @ConcordKey
    private final String secret;

    @JsonCreator
    public RepositoryEntry(@JsonProperty("name") String name,
                           @JsonProperty("url") String url,
                           @JsonProperty("branch") String branch,
                           @JsonProperty("commitId") String commitId,
                           @JsonProperty("secret") String secret) {

        this.name = name;
        this.url = url;
        this.branch = branch;
        this.commitId = commitId;
        this.secret = secret;
    }

    public String getName() {
        return name;
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

    @Override
    public String toString() {
        return "RepositoryEntry{" +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", branch='" + branch + '\'' +
                ", commitId='" + commitId + '\'' +
                ", secret='" + secret + '\'' +
                '}';
    }
}
