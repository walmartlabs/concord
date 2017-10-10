package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositoryEntry implements Serializable {

    private final UUID id;

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

    @Size(max = 2048)
    private final String path;

    @ConcordKey
    private final String secret;

    @JsonCreator
    public RepositoryEntry(@JsonProperty("id") UUID id,
                           @JsonProperty("name") String name,
                           @JsonProperty("url") String url,
                           @JsonProperty("branch") String branch,
                           @JsonProperty("commitId") String commitId,
                           @JsonProperty("path") String path,
                           @JsonProperty("secret") String secret) {

        this.id = id;
        this.name = name;
        this.url = url;
        this.branch = branch;
        this.commitId = commitId;
        this.path = path;
        this.secret = secret;
    }

    public UUID getId() {
        return id;
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

    public String getPath() {
        return path;
    }

    public String getSecret() {
        return secret;
    }

    @Override
    public String toString() {
        return "RepositoryEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", branch='" + branch + '\'' +
                ", commitId='" + commitId + '\'' +
                ", path='" + path + '\'' +
                ", secret='" + secret + '\'' +
                '}';
    }
}
