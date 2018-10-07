package com.walmartlabs.concord.server.org.policy;

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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class PolicyEntry implements Serializable {

    private final UUID id;

    @ConcordKey
    private final String name;

    private final Map<String, Object> rules;

    public PolicyEntry(String name, Map<String, Object> rules) {
        this(null, name, rules);
    }

    @JsonCreator
    public PolicyEntry(@JsonProperty("id") UUID id,
                       @JsonProperty("name") String name,
                       @JsonProperty("rules") Map<String, Object> rules) {
        this.id = id;
        this.name = name;
        this.rules = rules;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getRules() {
        return rules;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return rules == null || rules.isEmpty();
    }

    @Override
    public String toString() {
        return "PolicyEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", rules=" + rules +
                '}';
    }
}
