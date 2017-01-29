package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;

public class CreateProjectRequest implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    private final String[] templates;

    public CreateProjectRequest(String name) {
        this(name, null);
    }

    @JsonCreator
    public CreateProjectRequest(@JsonProperty("name") String name,
                                @JsonProperty("templates") String[] templates) {
        this.name = name;
        this.templates = templates;
    }

    public String getName() {
        return name;
    }

    public String[] getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "CreateProjectRequest{" +
                "name='" + name + '\'' +
                ", templates=" + Arrays.toString(templates) +
                '}';
    }
}
