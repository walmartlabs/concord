package com.walmartlabs.concord.server.api.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class InventoryEntry implements Serializable {

    private final UUID id;

    @NotNull
    @ConcordKey
    private final String name;

    private final UUID teamId;

    @ConcordKey
    private final String teamName;

    private final InventoryEntry parent;

    @JsonCreator
    public InventoryEntry(@JsonProperty("id") UUID id,
                          @JsonProperty("name") String name,
                          @JsonProperty("teamId") UUID teamId,
                          @JsonProperty("teamName") String teamName,
                          @JsonProperty("parent") InventoryEntry parent) {

        this.id = id;
        this.name = name;
        this.teamId = teamId;
        this.teamName = teamName;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public UUID getId() {
        return id;
    }

    public InventoryEntry getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return "InventoryEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", teamId=" + teamId +
                ", teamName='" + teamName + '\'' +
                ", parent=" + parent +
                '}';
    }
}
