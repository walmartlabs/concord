package com.walmartlabs.concord.process.loader.v1;

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
import com.walmartlabs.concord.runtime.model.*;
import io.takari.bpm.model.form.FormDefinition;

import java.io.Serializable;
import java.util.*;

/**
 * Adapter class for v1 ProjectDefinition.
 * {@see com.walmartlabs.concord.project.model.ProjectDefinition}
 */
public class ProcessDefinitionV1 extends EffectiveProcessDefinitionProviderV1 implements ProcessDefinition, Serializable {

    private static final long serialVersionUID = 1;

    private final Configuration cfg;
    private final Map<String, FlowDefinition> flows;
    private final Set<String> publicFlows;
    private final Map<String, Profile> profiles;
    private final List<Trigger> triggers;
    private final Imports imports;
    private final List<Form> forms;

    public ProcessDefinitionV1(com.walmartlabs.concord.project.model.ProjectDefinition delegate) {
        this.cfg = new ConfigurationV1(delegate.getConfiguration());

        this.flows = new HashMap<>();
        if (delegate.getFlows() != null) {
            delegate.getFlows().forEach((k, v) -> flows.put(k, new FlowDefinitionV1(v)));
        }

        this.publicFlows = new HashSet<>();
        if (delegate.getPublicFlows() != null) {
            publicFlows.addAll(delegate.getPublicFlows());
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

        this.forms = new ArrayList<>();
        if (delegate.getForms() != null) {
            delegate.getForms().forEach((formName, formDefinition) -> forms.add(Form.builder()
                    .name(formName)
                    .fields(toFields(formDefinition))
                    .build()));
        }
    }

    @Override
    public String runtime() {
        return "concord-v1"; // TODO constants
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
    public Set<String> publicFlows() {
        return publicFlows;
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

    @Override
    public List<Form> forms() {
        return forms;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<FormField> toFields(FormDefinition form) {
        if (form.getFields() == null) {
            return Collections.emptyList();
        }

        List<FormField> result = new ArrayList<>();
        form.getFields().forEach(f -> result.add(FormField.builder()
                .name(f.getName())
                .label(f.getLabel())
                .type(f.getType())
                .defaultValue((Serializable)f.getDefaultValue())
                .allowedValue((Serializable)f.getAllowedValue())
                .options(f.getOptions() != null ? (Map)f.getOptions() : Collections.emptyMap())
                .build()));
        return result;
    }
}
