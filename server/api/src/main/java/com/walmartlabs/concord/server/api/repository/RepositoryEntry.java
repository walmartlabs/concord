package com.walmartlabs.concord.server.api.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class RepositoryEntry implements Serializable {

    private final String id;
    private final String name;
    private final String url;

    @JsonCreator
    public RepositoryEntry(@JsonProperty("id") String id,
                           @JsonProperty("name") String name,
                           @JsonProperty("url") String url) {

        this.id = id;
        this.name = name;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "RepositoryEntry{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
