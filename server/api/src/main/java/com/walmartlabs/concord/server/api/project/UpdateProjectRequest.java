package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProjectRequest implements Serializable {

    private final String description;

    private final UUID teamId;

    @ConcordKey
    private final String teamName;

    private final Map<String, UpdateRepositoryRequest> repositories;

    private final Map<String, Object> cfg;

    public UpdateProjectRequest(ProjectEntry e) {
        this(e.getDescription(), e.getTeamId(), e.getTeamName(), e.getRepositories(), e.getCfg());
    }

    @JsonCreator
    public UpdateProjectRequest(@JsonProperty("description") String description,
                                @JsonProperty("teamId") UUID teamId,
                                @JsonProperty("teamName") String teamName,
                                @JsonProperty("repositories") Map<String, UpdateRepositoryRequest> repositories,
                                @JsonProperty("cfg") Map<String, Object> cfg) {

        this.description = description;
        this.teamId = teamId;
        this.teamName = teamName;
        this.repositories = repositories;
        this.cfg = cfg;
    }

    public String getDescription() {
        return description;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public Map<String, UpdateRepositoryRequest> getRepositories() {
        return repositories;
    }

    public Map<String, Object> getCfg() {
        return cfg;
    }

    @Override
    public String toString() {
        return "UpdateProjectRequest{" +
                "description='" + description + '\'' +
                ", teamId=" + teamId +
                ", teamName='" + teamName + '\'' +
                ", repositories=" + repositories +
                ", cfg=" + cfg +
                '}';
    }
}
