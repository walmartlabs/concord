package com.walmartlabs.concord.plugins.yaml2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    public JsonLocation getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @JsonIgnore
    public Object getOption(String k) {
        if (options == null) {
            return null;
        }
        return options.get(k);
    }

    @Override
    public String toString() {
        return "YamlFormField{" +
                "name='" + name + '\'' +
                ", options=" + options +
                '}';
    }
}
