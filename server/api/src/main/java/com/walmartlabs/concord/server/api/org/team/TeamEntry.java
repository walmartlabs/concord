package com.walmartlabs.concord.server.api.org.team;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public class TeamEntry implements Serializable {

    private final UUID id;

    private final UUID orgId;

    @ConcordKey
    private final String orgName;

    @NotNull
    @ConcordKey
    private final String name;

    @Size(max = 2048)
    private final String description;

    public TeamEntry(String name) {
        this(null, null, null, name, null);
    }

    @JsonCreator
    public TeamEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("orgId") UUID orgId,
                     @JsonProperty("orgName") String orgName,
                     @JsonProperty("name") String name,
                     @JsonProperty("description") String description) {

        this.id = id;
        this.orgId = orgId;
        this.orgName = orgName;
        this.name = name;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "TeamEntry{" +
                "id=" + id +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
