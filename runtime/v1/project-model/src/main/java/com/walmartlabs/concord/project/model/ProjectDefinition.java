package com.walmartlabs.concord.project.model;

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

import com.walmartlabs.concord.imports.Imports;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectDefinition extends Profile {

    private static final long serialVersionUID = 1L;

    private final Map<String, Profile> profiles;
    private final List<Trigger> triggers;
    private final Imports imports;
    private final Resources resources;

    public ProjectDefinition(Map<String, ProcessDefinition> flows,
                             Set<String> publicFlows,
                             Map<String, FormDefinition> forms,
                             Map<String, Object> configuration,
                             Map<String, Profile> profiles,
                             List<Trigger> triggers,
                             Imports imports,
                             Resources resources) {

        super(flows, publicFlows, forms, configuration);

        this.profiles = profiles;
        this.triggers = triggers;
        this.imports = imports;
        this.resources = resources;
    }

    public ProjectDefinition(ProjectDefinition src, Imports imports) {
        this(
                src.getFlows(),
                src.getPublicFlows(),
                src.getForms(),
                src.getConfiguration(),
                src.getProfiles(),
                src.getTriggers(),
                imports,
                src.getResources()
        );
    }

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public List<Trigger> getTriggers() {
        return triggers;
    }

    public Imports getImports() {
        return imports;
    }

    public Resources getResources() {
        return resources;
    }

    @Override
    public String toString() {
        return "ProjectDefinition{" +
                "profiles=" + profiles +
                ", triggers=" + triggers +
                ", imports=" + imports +
                ", resources=" + resources +
                "} " + super.toString();
    }
}
