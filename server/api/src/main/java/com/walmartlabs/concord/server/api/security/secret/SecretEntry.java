package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class SecretEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    @NotNull
    private final SecretType type;

    @JsonCreator
    public SecretEntry(@JsonProperty("name") String name,
                       @JsonProperty("type") SecretType type) {

        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public SecretType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "SecretEntry{" +
                "name='" + name + '\'' +
                ", type=" + type +
                '}';
    }
}
