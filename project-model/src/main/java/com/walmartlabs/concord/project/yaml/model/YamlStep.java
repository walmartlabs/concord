package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.io.Serializable;

public abstract class YamlStep implements Serializable {

    private final JsonLocation location;

    protected YamlStep(JsonLocation location) {
        this.location = location;
    }

    public JsonLocation getLocation() {
        return location;
    }
}
