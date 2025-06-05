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

import java.util.List;
import java.util.Map;
import java.util.Set;

public class YamlProject extends YamlProfile {

    private static final long serialVersionUID = 1L;

    private final Map<String, YamlProfile> profiles;
    private final List<YamlTrigger> triggers;
    private final List<YamlImport> imports;
    private final Map<String, Object> resources;

    @JsonCreator
    public YamlProject(@JsonProperty("flows") Map<String, List<YamlStep>> flows,
                       @JsonProperty("publicFlows") Set<String> publicFlows,
                       @JsonProperty("forms") Map<String, List<YamlFormField>> forms,
                       @JsonProperty("configuration") Map<String, Object> configuration,
                       @JsonProperty("variables") Map<String, Object> variables,
                       @JsonProperty("profiles") Map<String, YamlProfile> profiles,
                       @JsonProperty("triggers") List<YamlTrigger> triggers,
                       @JsonProperty("imports") List<YamlImport> imports,
                       @JsonProperty("resources") Map<String, Object> resources) {

        super(flows, publicFlows, forms, configuration, variables);
        this.profiles = profiles;
        this.triggers = triggers;
        this.imports = imports;
        this.resources = resources;
    }

    public Map<String, YamlProfile> getProfiles() {
        return profiles;
    }

    public List<YamlTrigger> getTriggers() {
        return triggers;
    }

    public List<YamlImport> getImports() {
        return imports;
    }

    public Map<String, Object> getResources() {
        return resources;
    }

    @Override
    public String toString() {
        return "YamlProject{" +
                "profiles=" + profiles +
                ", triggers=" + triggers +
                ", imports=" + imports +
                ", resources=" + resources +
                "} " + super.toString();
    }
}
