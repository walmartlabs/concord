package com.walmartlabs.concord.plugins.yaml2.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.util.Map;

public class YamlFormCall extends YamlStep {

    private final String key;
    private final Map<String, Object> options;

    public YamlFormCall(JsonLocation location, String key, Map<String, Object> options) {
        super(location);
        this.key = key;
        this.options = options;
    }

    @Override
    public String toString() {
        return "YamlFormCall{" +
                "key='" + key + '\'' +
                ", options=" + options +
                '}';
    }
}
