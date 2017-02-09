package com.walmartlabs.concord.server.api.repository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.IdName;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositoryEntry implements Serializable {

    private final String id;
    private final String name;
    private final String url;
    private final String branch;
    private final IdName secret;

    @JsonCreator
    public RepositoryEntry(@JsonProperty("id") String id,
                           @JsonProperty("name") String name,
                           @JsonProperty("url") String url,
                           @JsonProperty("branch") String branch,
                           @JsonProperty("secret") IdName secret) {

        this.id = id;
        this.name = name;
        this.url = url;
        this.branch = branch;
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

    public String getBranch() {
        return branch;
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
                ", branch='" + branch + '\'' +
                ", secret=" + secret +
                '}';
    }
}
