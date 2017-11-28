package com.walmartlabs.concord.server.api.org.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class ProjectOwner implements Serializable {

    private final UUID id;
    private final String username;

    @JsonCreator
    public ProjectOwner(@JsonProperty("id") UUID id,
                        @JsonProperty("username") String username) {

        this.id = id;
        this.username = username;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "ProjectOwner{" +
                "id=" + id +
                ", username='" + username + '\'' +
                '}';
    }
}
