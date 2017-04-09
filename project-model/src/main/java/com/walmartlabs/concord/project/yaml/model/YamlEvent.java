package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

public class YamlEvent extends YamlStep {

    private final String name;

    public YamlEvent(JsonLocation location, String name) {
        super(location);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "YamlEvent{" +
                "name='" + name + '\'' +
                '}';
    }
}
