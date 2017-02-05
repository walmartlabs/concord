package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Arrays;

public class UpdateProjectRequest implements Serializable {

    private final String[] templates;

    @JsonCreator
    public UpdateProjectRequest(@JsonProperty("templates") String[] templates) {
        this.templates = templates;
    }

    public String[] getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "UpdateProjectRequest{" +
                "templates=" + Arrays.toString(templates) +
                '}';
    }
}
