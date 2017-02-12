package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.IdName;

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

    private final IdName secret;

    @JsonCreator
    public RepositoryEntry(@JsonProperty("name") String name,
                           @JsonProperty("url") String url,
                           @JsonProperty("branch") String branch,
                           @JsonProperty("secret") IdName secret) {

        this.name = name;
        this.url = url;
        this.branch = branch;
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

    public IdName getSecret() {
        return secret;
    }

    @Override
    public String toString() {
        return "RepositoryEntry{" +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", branch='" + branch + '\'' +
                ", secret=" + secret +
                '}';
    }
}
