package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

public class CreateProjectRequest implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    private final Set<String> templates;

    @JsonCreator
    public CreateProjectRequest(@JsonProperty("name") String name,
                                @JsonProperty("templates") Set<String> templates) {
        this.name = name;
        this.templates = templates;
    }

    public String getName() {
        return name;
    }

    public Set<String> getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "CreateProjectRequest{" +
                "name='" + name + '\'' +
                ", templates=" + templates +
                '}';
    }
}
