package com.walmartlabs.concord.plugins.yaml2.model;

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
