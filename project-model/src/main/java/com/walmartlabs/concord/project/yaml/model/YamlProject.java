package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class YamlProject extends YamlProfile {

    private final Map<String, YamlProfile> profiles;

    @JsonCreator
    public YamlProject(@JsonProperty("flows") Map<String, List<YamlStep>> flows,
                       @JsonProperty("forms") Map<String, List<YamlFormField>> forms,
                       @JsonProperty("variables") Map<String, Object> variables,
                       @JsonProperty("profiles") Map<String, YamlProfile> profiles) {

        super(flows, forms, variables);
        this.profiles = profiles;
    }

    public Map<String, YamlProfile> getProfiles() {
        return profiles;
    }

    @Override
    public String toString() {
        return "YamlProject{" +
                "profiles=" + profiles +
                "} " + super.toString();
    }
}
