package com.walmartlabs.concord.server.api.team.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class SecretEntry implements Serializable {

    private final UUID id;

    @NotNull
    @ConcordKey
    private final String name;

    private final UUID teamId;

    @ConcordKey
    private final String teamName;

    @NotNull
    private final SecretType type;

    private final SecretStoreType storeType;

    @JsonCreator
    public SecretEntry(@JsonProperty("id") UUID id,
                       @JsonProperty("name") String name,
                       @JsonProperty("teamId") UUID teamId,
                       @JsonProperty("teamName") String teamName,
                       @JsonProperty("type") SecretType type,
                       @JsonProperty("storeType") SecretStoreType storeType) {

        this.id = id;
        this.name = name;
        this.teamId = teamId;
        this.teamName = teamName;
        this.type = type;
        this.storeType = storeType;
    }

    public UUID getId() {
        return id;
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

    public SecretType getType() {
        return type;
    }

    public SecretStoreType getStoreType() {
        return storeType;
    }

    @Override
    public String toString() {
        return "SecretEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", teamId=" + teamId +
                ", teamName='" + teamName + '\'' +
                ", type=" + type +
                ", storeType=" + storeType +
                '}';
    }
}
