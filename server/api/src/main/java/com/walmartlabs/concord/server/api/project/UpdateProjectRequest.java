package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProjectRequest implements Serializable {

    private final String description;

    private final UUID orgId;

    @ConcordKey
    private final String orgName;

    private final Map<String, RepositoryEntry> repositories;

    private final Map<String, Object> cfg;

    public UpdateProjectRequest(ProjectEntry e) {
        this(e.getDescription(), e.getOrgId(), e.getOrgName(), e.getRepositories(), e.getCfg());
    }

    @JsonCreator
    public UpdateProjectRequest(@JsonProperty("description") String description,
                                @JsonProperty("orgId") UUID orgId,
                                @JsonProperty("orgName") String orgName,
                                @JsonProperty("repositories") Map<String, RepositoryEntry> repositories,
                                @JsonProperty("cfg") Map<String, Object> cfg) {

        this.description = description;
        this.orgId = orgId;
        this.orgName = orgName;
        this.repositories = repositories;
        this.cfg = cfg;
    }

    public String getDescription() {
        return description;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public Map<String, RepositoryEntry> getRepositories() {
        return repositories;
    }

    public Map<String, Object> getCfg() {
        return cfg;
    }

    @Override
    public String toString() {
        return "UpdateProjectRequest{" +
                "description='" + description + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", repositories=" + repositories +
                ", cfg=" + cfg +
                '}';
    }
}
