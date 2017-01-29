package com.walmartlabs.concord.server.api.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class CreateRepositoryRequest implements Serializable {

    @NotNull
    @ConcordId
    private final String projectId;

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    private final String url;

    @JsonCreator
    public CreateRepositoryRequest(@JsonProperty("projectId") String projectId, @JsonProperty("name") String name, @JsonProperty("url") String url) {
        this.projectId = projectId;
        this.name = name;
        this.url = url;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "CreateRepositoryRequest{" +
                "projectId='" + projectId + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
