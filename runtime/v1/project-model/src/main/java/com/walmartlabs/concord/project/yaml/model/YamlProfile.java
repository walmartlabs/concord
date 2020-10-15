package com.walmartlabs.concord.project.yaml.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class YamlProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, List<YamlStep>> flows;
    private final Set<String> publicFlows;
    private final Map<String, List<YamlFormField>> forms;
    private final Map<String, Object> configuration;

    @JsonCreator
    public YamlProfile(@JsonProperty("flows") Map<String, List<YamlStep>> flows,
                       @JsonProperty("publicFlows") Set<String> publicFlows,
                       @JsonProperty("forms") Map<String, List<YamlFormField>> forms,
                       @JsonProperty("configuration") Map<String, Object> configuration,
                       @JsonProperty("variables") Map<String, Object> variables) {

        this.flows = flows;
        this.publicFlows = publicFlows;
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

    public Set<String> getPublicFlows() {
        return publicFlows;
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
                ", publicFlows=" + publicFlows +
                ", forms=" + forms +
                ", configuration=" + configuration +
                '}';
    }
}
