package com.walmartlabs.concord.server.process.loader.v1;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.server.process.loader.model.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter class for v1 ProjectDefinition.
 * {@see com.walmartlabs.concord.project.model.ProjectDefinition}
 */
public class ProjectDefinitionV1 implements ProjectDefinition, Serializable {

    private static final long serialVersionUID = 1;

    private final Configuration cfg;
    private final Map<String, FlowDefinition> flows;
    private final Map<String, Profile> profiles;
    private final List<Trigger> triggers;
    private final Imports imports;

    public ProjectDefinitionV1(com.walmartlabs.concord.project.model.ProjectDefinition delegate) {
        this.cfg = new ConfigurationV1(delegate.getConfiguration());

        this.flows = new HashMap<>();
        if (delegate.getFlows() != null) {
            delegate.getFlows().forEach((k, v) -> flows.put(k, new FlowDefinitionV1()));
        }

        this.profiles = new HashMap<>();
        if (delegate.getProfiles() != null) {
            delegate.getProfiles().forEach((k, v) -> profiles.put(k, new ProfileV1(v)));
        }

        this.triggers = new ArrayList<>();
        if (delegate.getTriggers() != null) {
            delegate.getTriggers().forEach(t -> triggers.add(new TriggerV1(t)));
        }

        this.imports = delegate.getImports();
    }

    @Override
    public Configuration configuration() {
        return cfg;
    }

    @Override
    public Map<String, FlowDefinition> flows() {
        return flows;
    }

    @Override
    public Map<String, Profile> profiles() {
        return profiles;
    }

    @Override
    public List<Trigger> triggers() {
        return triggers;
    }

    @Override
    public Imports imports() {
        return imports;
    }
}
