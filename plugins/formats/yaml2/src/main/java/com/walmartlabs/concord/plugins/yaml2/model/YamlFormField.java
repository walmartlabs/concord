package com.walmartlabs.concord.plugins.yaml2.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.io.Serializable;
import java.util.Map;

public class YamlFormField implements Serializable {

    private final JsonLocation location;
    private final String name;
    private final Map<String, Object> options;

    public YamlFormField(JsonLocation location, String name, Map<String, Object> options) {
        this.location = location;
        this.name = name;
        this.options = options;
    }

    @Override
    public String toString() {
        return "YamlFormField{" +
                "name='" + name + '\'' +
                ", options=" + options +
                '}';
    }
}
