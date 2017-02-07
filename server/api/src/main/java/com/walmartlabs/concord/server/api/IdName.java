package com.walmartlabs.concord.server.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class IdName implements Serializable {

    private final String id;
    private final String name;

    @JsonCreator
    public IdName(@JsonProperty("id") String id, @JsonProperty("name") String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "IdName{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
