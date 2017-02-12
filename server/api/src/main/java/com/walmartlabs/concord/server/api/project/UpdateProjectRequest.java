package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

public class UpdateProjectRequest implements Serializable {

    private final Set<String> templates;

    @JsonCreator
    public UpdateProjectRequest(@JsonProperty("templates") Set<String> templates) {
        this.templates = templates;
    }

    public Set<String> getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "UpdateProjectRequest{" +
                "templates=" + templates +
                '}';
    }
}
