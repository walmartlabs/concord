package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class SecretEntry implements Serializable {

    private final String id;
    private final String name;
    private final SecretType type;

    @JsonCreator
    public SecretEntry(@JsonProperty("id") String id, @JsonProperty("name") String name, @JsonProperty("type") SecretType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public String getId() {
        return id;
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
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                '}';
    }
}
