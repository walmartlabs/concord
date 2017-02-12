package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProjectRequest implements Serializable {

    private final Set<String> templates;
    private final Map<String, UpdateRepositoryRequest> repositories;

    @JsonCreator
    public UpdateProjectRequest(@JsonProperty("templates") Set<String> templates,
                                @JsonProperty("repositories") Map<String, UpdateRepositoryRequest> repositories) {

        this.templates = templates;
        this.repositories = repositories;
    }

    public Set<String> getTemplates() {
        return templates;
    }

    public Map<String, UpdateRepositoryRequest> getRepositories() {
        return repositories;
    }

    @Override
    public String toString() {
        return "UpdateProjectRequest{" +
                "templates=" + templates +
                ", repositories=" + repositories +
                '}';
    }
}
