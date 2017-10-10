package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

public class SecretEntry implements Serializable {

    private final UUID id;

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    private final SecretType type;

    private final SecretStoreType storeType;

    @JsonCreator
    public SecretEntry(@JsonProperty("id") UUID id,
                       @JsonProperty("name") String name,
                       @JsonProperty("type") SecretType type,
                       @JsonProperty("storeType") SecretStoreType storeType) {

        this.id = id;
        this.name = name;
        this.type = type;
        this.storeType = storeType;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
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
                ", type=" + type +
                ", storeType=" + storeType +
                '}';
    }
}
