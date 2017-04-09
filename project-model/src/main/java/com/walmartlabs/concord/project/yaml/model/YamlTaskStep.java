package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.util.Map;

public class YamlTaskStep extends YamlStep {

    private final String key;
    private final Map<String, Object> options;

    public YamlTaskStep(JsonLocation location, String key, Map<String, Object> options) {
        super(location);
        this.key = key;
        this.options = options;
    }

    public String getKey() {
        return key;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "YamlTaskStep{" +
                "key='" + key + '\'' +
                ", options=" + options +
                '}';
    }
}
