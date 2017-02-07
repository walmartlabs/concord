package com.walmartlabs.concord.server.api.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.IdName;

import java.io.Serializable;

public class RepositoryEntry implements Serializable {

    private final String id;
    private final String name;
    private final String url;
    private final IdName secret;

    @JsonCreator
    public RepositoryEntry(@JsonProperty("id") String id,
                           @JsonProperty("name") String name,
                           @JsonProperty("url") String url,
                           @JsonProperty("secret") IdName secret) {

        this.id = id;
        this.name = name;
        this.url = url;
        this.secret = secret;
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

    public IdName getSecret() {
        return secret;
    }

    @Override
    public String toString() {
        return "RepositoryEntry{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", secret=" + secret +
                '}';
    }
}
