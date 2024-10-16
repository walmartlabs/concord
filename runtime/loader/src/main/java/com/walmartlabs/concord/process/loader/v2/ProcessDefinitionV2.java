package com.walmartlabs.concord.process.loader.v2;

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
import com.walmartlabs.concord.process.loader.model.*;

import java.io.Serializable;
import java.util.*;

public class ProcessDefinitionV2 extends EffectiveProcessDefinitionProviderV2 implements ProcessDefinition, Serializable {

    private static final long serialVersionUID = 1L;

    private final Configuration cfg;
    private final Map<String, FlowDefinition> flows;
    private final Set<String> publicFlows;
    private final Map<String, Profile> profiles;
    private final List<Trigger> triggers;
    private final Imports imports;
    private final List<Form> forms;

    public ProcessDefinitionV2(com.walmartlabs.concord.runtime.v2.model.ProcessDefinition delegate) {
        super(delegate);

        this.cfg = new ConfigurationV2(delegate.configuration());

        this.flows = new HashMap<>();
        if (delegate.flows() != null) {
            delegate.flows().forEach((k, v) -> flows.put(k, new FlowDefinitionV2(k, v.steps())));
        }

        this.publicFlows = new HashSet<>();
        if (delegate.publicFlows() != null) {
            this.publicFlows.addAll(delegate.publicFlows());
        }

        this.profiles = new HashMap<>();
        if (delegate.profiles() != null) {
            delegate.profiles().forEach((k, v) -> profiles.put(k, new ProfileV2(v)));
        }

        this.triggers = new ArrayList<>();
        if (delegate.triggers() != null) {
            delegate.triggers().forEach(t -> triggers.add(new TriggerV2(t)));
        }

        this.imports = delegate.imports();
        this.forms = new ArrayList<>();
        if (delegate.forms() != null) {
            delegate.forms().values().forEach(f -> forms.add(Form.builder()
                    .name(f.name())
                    .fields(toFields(f))
                    .location(SourceMap.from(f.location()))
                    .build()));
        }
    }

    @Override
    public String runtime() {
        return "concord-v2";
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

    private static List<FormField> toFields(com.walmartlabs.concord.runtime.v2.model.Form form) {
        List<FormField> fields = new ArrayList<>();
        form.fields().forEach(f -> fields.add(FormField.builder()
                .name(f.name())
                .label(f.label())
                .type(f.type())
                .defaultValue(f.defaultValue())
                .allowedValue(f.allowedValue())
                .options(f.options())
                .location(SourceMap.from(f.location()))
                .build()));
        return fields;
    }
}
