package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

public class YamlReturn extends YamlStep {

    public YamlReturn(JsonLocation location) {
        super(location);
    }

    @Override
    public String toString() {
        return "YamlReturn{}";
    }
}
