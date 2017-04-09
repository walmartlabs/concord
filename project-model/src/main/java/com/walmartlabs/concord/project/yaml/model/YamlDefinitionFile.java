package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.io.Serializable;
import java.util.Map;

public class YamlDefinitionFile implements Serializable {

    private final Map<String, YamlDefinition> definitions;

    @JsonCreator
    public YamlDefinitionFile(@JsonUnwrapped Map<String, YamlDefinition> definitions) {
        this.definitions = definitions;
    }

    public Map<String, YamlDefinition> getDefinitions() {
        return definitions;
    }

    @Override
    public String toString() {
        return "YamlDefinitionFile{" +
                "definitions=" + definitions +
                '}';
    }
}
