package com.walmartlabs.concord.project.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.io.Serializable;
import java.util.Map;

public class Profile implements Serializable {

    private final Map<String, ProcessDefinition> flows;
    private final Map<String, FormDefinition> forms;
    private final Map<String, Object> configuration;

    public Profile(Map<String, ProcessDefinition> flows,
                   Map<String, FormDefinition> forms,
                   Map<String, Object> configuration) {

        this.flows = flows;
        this.forms = forms;
        this.configuration = configuration;
    }

    public Map<String, ProcessDefinition> getFlows() {
        return flows;
    }

    public Map<String, FormDefinition> getForms() {
        return forms;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public String toString() {
        return "Profile{" +
                "flows=" + flows +
                ", forms=" + forms +
                ", variables=" + configuration +
                '}';
    }
}
