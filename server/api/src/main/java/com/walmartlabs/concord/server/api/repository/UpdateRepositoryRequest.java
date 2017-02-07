package com.walmartlabs.concord.server.api.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class UpdateRepositoryRequest implements Serializable {

    @NotNull
    private final String url;

    @ConcordKey
    private String secret;

    public UpdateRepositoryRequest(String url) {
        this(url, null);
    }

    @JsonCreator
    public UpdateRepositoryRequest(@JsonProperty("url") String url,
                                   @JsonProperty("secret") String secret) {
        this.url = url;
        this.secret = secret;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    @Override
    public String toString() {
        return "UpdateRepositoryRequest{" +
                "url='" + url + '\'' +
                ", secret='" + secret + '\'' +
                '}';
    }
}
