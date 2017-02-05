package com.walmartlabs.concord.server.api.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordId;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class UpdateRepositoryRequest implements Serializable {

    @NotNull
    private final String url;

    @JsonCreator
    public UpdateRepositoryRequest(@JsonProperty("url") String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "UpdateRepositoryRequest{" +
                "url='" + url + '\'' +
                '}';
    }
}
