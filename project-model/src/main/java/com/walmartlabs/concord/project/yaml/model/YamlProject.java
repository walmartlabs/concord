package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.project.model.Trigger;

import java.util.List;
import java.util.Map;

public class YamlProject extends YamlProfile {

    private final Map<String, YamlProfile> profiles;

    private final List<Map<String, Map<String, Object>>> triggers;

    @JsonCreator
    public YamlProject(@JsonProperty("flows") Map<String, List<YamlStep>> flows,
                       @JsonProperty("forms") Map<String, List<YamlFormField>> forms,
                       @JsonProperty("configuration") Map<String, Object> configuration,
                       @JsonProperty("variables") Map<String, Object> variables,
                       @JsonProperty("profiles") Map<String, YamlProfile> profiles,
                       @JsonProperty("triggers") List<Map<String, Map<String, Object>>> triggers) {

        super(flows, forms, configuration, variables);
        this.profiles = profiles;
        this.triggers = triggers;
    }

    public Map<String, YamlProfile> getProfiles() {
        return profiles;
    }

    public List<Map<String, Map<String, Object>>> getTriggers() {
        return triggers;
    }

    @Override
    public String toString() {
        return "YamlProject{" +
                "profiles=" + profiles +
                "triggers=" + triggers +
                "} " + super.toString();
    }
}
