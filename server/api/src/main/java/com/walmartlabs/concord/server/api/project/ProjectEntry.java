package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;

    @Size(max = 1024)
    private final String description;

    private final Set<String> templates;

    @JsonCreator
    public ProjectEntry(@JsonProperty("name") String name,
                        @JsonProperty("description") String description,
                        @JsonProperty("templates") Set<String> templates) {

        this.name = name;
        this.description = description;
        this.templates = templates;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "ProjectEntry{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", templates=" + templates +
                '}';
    }
}
