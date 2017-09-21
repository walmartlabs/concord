package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class YamlProfile implements Serializable {

    private final Map<String, List<YamlStep>> flows;
    private final Map<String, List<YamlFormField>> forms;
    private final Map<String, Object> configuration;

    @JsonCreator
    public YamlProfile(@JsonProperty("flows") Map<String, List<YamlStep>> flows,
                       @JsonProperty("forms") Map<String, List<YamlFormField>> forms,
                       @JsonProperty("configuration") Map<String, Object> configuration,
                       @JsonProperty("variables") Map<String, Object> variables) {

        this.flows = flows;
        this.forms = forms;

        // alias "variables" to "configuration"
        if (configuration != null) {
            this.configuration = configuration;
        } else {
            this.configuration = variables;
        }
    }

    public Map<String, List<YamlStep>> getFlows() {
        return flows;
    }

    public Map<String, List<YamlFormField>> getForms() {
        return forms;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public String toString() {
        return "YamlProfile{" +
                "flows=" + flows +
                ", forms=" + forms +
                ", configuration=" + configuration +
                '}';
    }
}
