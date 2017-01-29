package com.walmartlabs.concord.server.api.project;

import java.io.Serializable;
import java.util.Arrays;

public class ProjectEntry implements Serializable {

    private final String id;
    private final String name;
    private final String[] templates;

    public ProjectEntry(String id, String name, String[] templates) {
        this.id = id;
        this.name = name;
        this.templates = templates;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String[] getTemplates() {
        return templates;
    }

    @Override
    public String toString() {
        return "ProjectEntry{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", templates=" + Arrays.toString(templates) +
                '}';
    }
}
