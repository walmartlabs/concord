package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

public class UpdateRepositoryRequest implements Serializable {

    @NotNull
    private final String url;

    @Size(max = 255)
    private final String branch;

    @ConcordKey
    private final String secret;

    @JsonCreator
    public UpdateRepositoryRequest(@JsonProperty("url") String url,
                                   @JsonProperty("branch") String branch,
                                   @JsonProperty("secret") String secret) {

        this.url = url;
        this.branch = branch;
        this.secret = secret;
    }

    public String getUrl() {
        return url;
    }

    public String getBranch() {
        return branch;
    }

    public String getSecret() {
        return secret;
    }

    @Override
    public String toString() {
        return "UpdateRepositoryRequest{" +
                "url='" + url + '\'' +
                ", branch='" + branch + '\'' +
                ", secret='" + secret + '\'' +
                '}';
    }
}
