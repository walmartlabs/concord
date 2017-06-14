package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.util.Map;

public class YamlSetVariablesStep extends YamlStep {

    private final Map<String, Object> variables;

    public YamlSetVariablesStep(JsonLocation location, Map<String, Object> variables) {
        super(location);

        this.variables = variables;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }
}
