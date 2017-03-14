package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class CreateProjectRequest implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    private final Set<String> templates;

    private final Map<String, UpdateRepositoryRequest> repositories;

    private final Map<String, Object> cfg;

    public CreateProjectRequest(String name, Map<String, Object> cfg) {
        this(name, null, null, cfg);
    }

    public CreateProjectRequest(String name, Set<String> templates, Map<String, UpdateRepositoryRequest> repositories) {
        this(name, templates, repositories, null);
    }

    @JsonCreator
    public CreateProjectRequest(@JsonProperty("name") String name,
                                @JsonProperty("templates") Set<String> templates,
                                @JsonProperty("repositories") Map<String, UpdateRepositoryRequest> repositories,
                                @JsonProperty("cfg") Map<String, Object> cfg) {
        this.name = name;
        this.templates = templates;
        this.repositories = repositories;
        this.cfg = cfg;
    }

    public String getName() {
        return name;
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
                ", templates=" + templates +
                ", repositories=" + repositories +
                ", cfg=" + cfg +
                '}';
    }
}
