package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.annotation.*;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class YamlProfile implements Serializable {

    private final Map<String, List<YamlStep>> flows;
    private final Map<String, List<YamlFormField>> forms;
    private final Map<String, Object> configuration;

    @JsonCreator
    public YamlProfile(@JsonProperty("flows") Map<String, List<YamlStep>> flows,
                       @JsonProperty("forms") Map<String, List<YamlFormField>> forms,
                       @JsonProperty("configuration") Map<String, Object> configuration,
                       @JsonProperty("variables") Map<String, Object> variables) {

        this.flows = removeNullElements(flows);
        this.forms = removeNullElements(forms);

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

    private static <K, V> Map<K, List<V>> removeNullElements(Map<K, List<V>> items) {
        if (items == null) {
            return null;
        }

        Map<K, List<V>> result = new HashMap<>();
        items.forEach((k, v) -> {
            if (v == null) {
                return;
            }

            List<V> l = v.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            result.put(k, l);
        });

        return result;
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
