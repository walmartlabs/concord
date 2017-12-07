package com.walmartlabs.concord.server.api.org.secret;

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

    private final UUID orgId;

    @ConcordKey
    private final String orgName;

    @NotNull
    private final SecretType type;

    private final SecretStoreType storeType;

    private final SecretVisibility visibility;

    private final SecretOwner owner;

    @JsonCreator
    public SecretEntry(@JsonProperty("id") UUID id,
                       @JsonProperty("name") String name,
                       @JsonProperty("orgId") UUID orgId,
                       @JsonProperty("orgName") String orgName,
                       @JsonProperty("type") SecretType type,
                       @JsonProperty("storeType") SecretStoreType storeType,
                       @JsonProperty("visibility") SecretVisibility visibility,
                       @JsonProperty("owner") SecretOwner owner) {

        this.id = id;
        this.name = name;
        this.orgId = orgId;
        this.orgName = orgName;
        this.type = type;
        this.storeType = storeType;
        this.visibility = visibility;
        this.owner = owner;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public SecretType getType() {
        return type;
    }

    public SecretStoreType getStoreType() {
        return storeType;
    }

    public SecretVisibility getVisibility() {
        return visibility;
    }

    public SecretOwner getOwner() {
        return owner;
    }

    @Override
    public String toString() {
        return "SecretEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", type=" + type +
                ", storeType=" + storeType +
                ", visibility=" + visibility +
                ", owner=" + owner +
                '}';
    }
}
