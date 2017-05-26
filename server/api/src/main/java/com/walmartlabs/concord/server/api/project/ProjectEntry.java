package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class ProjectEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    @Size(max = 1024)
    private final String description;

    private final Set<String> templates;

    private final Map<String, UpdateRepositoryRequest> repositories;

    private final Map<String, Object> cfg;

    @JsonCreator
    public ProjectEntry(@JsonProperty("name") String name,
                        @JsonProperty("description") String description,
                        @JsonProperty("templates") Set<String> templates,
                        @JsonProperty("repositories") Map<String, UpdateRepositoryRequest> repositories,
                        @JsonProperty("cfg") Map<String, Object> cfg) {

        this.name = name;
        this.description = description;
        this.templates = templates;
        this.repositories = repositories;
        this.cfg = cfg;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getTemplates() {
        return templates;
    }

    public Map<String, UpdateRepositoryRequest> getRepositories() {
        return repositories;
    }

    public Map<String, Object> getCfg() {
        return cfg;
    }

    @Override
    public String toString() {
        return "CreateProjectRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", templates=" + templates +
                ", repositories=" + repositories +
                ", cfg=" + cfg +
                '}';
    }
}
