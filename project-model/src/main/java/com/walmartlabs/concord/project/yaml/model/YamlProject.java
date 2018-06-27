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
