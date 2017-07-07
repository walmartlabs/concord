package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.io.Serializable;
import java.util.Map;

public class YamlProfileFile implements Serializable {

    private final Map<String, YamlProfile> profiles;

    @JsonCreator
    public YamlProfileFile(@JsonUnwrapped Map<String, YamlProfile> profiles) {
        this.profiles = profiles;
    }

    public Map<String, YamlProfile> getProfiles() {
        return profiles;
    }

    @Override
    public String toString() {
        return "YamlProfileFile{" +
                "profiles=" + profiles +
                '}';
    }
}
