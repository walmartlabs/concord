package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    private final Set<String> templates;

    @JsonCreator
    public ProjectEntry(@JsonProperty("name") String name,
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
        return "ProjectEntry{" +
                "name='" + name + '\'' +
                ", templates=" + templates +
                '}';
    }
}
