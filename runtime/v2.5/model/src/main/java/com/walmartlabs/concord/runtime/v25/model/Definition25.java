package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.runtime.model.Form;
import com.walmartlabs.concord.runtime.model.Options;
import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.model.Trigger;

import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
public record Definition25(Configuration25 configuration, Map<String, Flow25> flows,
                           Set<String> publicFlows, Map<String, Profile25> profiles,
                           List<Trigger> triggers, Imports imports, Map<String, Form> formDefinitions,
                           Map<String, Object> resources, Map<String, Object> raw,
                           SourceRange importsRange) implements ProcessDefinition, Serializable {

    public static final String RUNTIME_TYPE = "concord-v2.5";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Definition25 {
        flows = Collections.unmodifiableMap(new LinkedHashMap<>(flows));
        publicFlows = Values.set(publicFlows);
        profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        triggers = Values.list(triggers);
        formDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(formDefinitions));
        resources = Values.map(resources);
        raw = Values.map(raw);
    }

    @Override
    public String runtime() {
        return RUNTIME_TYPE;
    }

    @Override
    public List<Form> forms() {
        return List.copyOf(formDefinitions.values());
    }

    @Override
    public void serialize(Options options, OutputStream out) throws Exception {
        var effective = effective(options.activeProfiles());
        var configuration = new LinkedHashMap<>(effective.configuration().values());
        var arguments = optionMap(options.configuration().get("arguments"));
        arguments.put("txId", options.instanceId());
        if (options.parentInstanceId() != null) {
            arguments.put("parentInstanceId", options.parentInstanceId());
        }
        arguments.put("initiator", options.configuration().get("initiator"));
        arguments.put("projectInfo", optionMap(options.configuration().get("projectInfo")));
        arguments.put("processInfo", optionMap(options.configuration().get("processInfo")));
        configuration.put("entryPoint", options.entryPoint());
        configuration.put("arguments", arguments);

        var raw = new LinkedHashMap<>(effective.raw());
        raw.put("configuration", configuration);
        raw.remove("imports");
        raw.remove("profiles");
        OBJECT_MAPPER.writeValue(out, raw);
    }

    public Definition25 effective(List<String> activeProfiles) {
        if (activeProfiles.isEmpty()) {
            return this;
        }
        var effectiveConfiguration = new LinkedHashMap<>(configuration.values());
        var effectiveFlows = new LinkedHashMap<>(flows);
        var effectiveForms = new LinkedHashMap<>(formDefinitions);
        var effectiveRaw = new LinkedHashMap<>(raw);
        var effectiveRawConfiguration = rawMap(raw.get("configuration"));
        var effectiveRawFlows = rawMap(raw.get("flows"));
        var effectiveRawForms = rawMap(raw.get("forms"));
        var rawProfiles = rawMap(raw.get("profiles"));
        for (var name : activeProfiles) {
            var profile = profiles.get(name);
            if (profile == null) {
                continue;
            }
            effectiveConfiguration = deepMerge(effectiveConfiguration, profile.configuration().values());
            effectiveFlows.putAll(profile.flows());
            effectiveForms.putAll(profile.forms());

            var rawProfile = rawMap(rawProfiles.get(name));
            effectiveRawConfiguration = deepMerge(effectiveRawConfiguration, rawMap(rawProfile.get("configuration")));
            effectiveRawFlows.putAll(rawMap(rawProfile.get("flows")));
            effectiveRawForms.putAll(rawMap(rawProfile.get("forms")));
        }
        effectiveRaw.put("configuration", effectiveRawConfiguration);
        effectiveRaw.put("flows", effectiveRawFlows);
        effectiveRaw.put("forms", effectiveRawForms);
        return new Definition25(new Configuration25(effectiveConfiguration, configuration.sourceRange()), effectiveFlows,
                publicFlows, profiles, triggers, imports, effectiveForms, resources, effectiveRaw, importsRange);
    }

    @SuppressWarnings("unchecked")
    public static LinkedHashMap<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        return deepMerge(base, overlay, new IdentityHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay,
                                                            IdentityHashMap<Map<?, ?>,
                                                                    IdentityHashMap<Map<?, ?>,
                                                                            LinkedHashMap<String, Object>>> seen) {
        var existing = seen.get(base);
        if (existing != null && existing.containsKey(overlay)) {
            return existing.get(overlay);
        }
        var result = new LinkedHashMap<String, Object>(base);
        seen.computeIfAbsent(base, ignored -> new IdentityHashMap<>()).put(overlay, result);
        overlay.forEach((key, value) -> {
            var previous = result.get(key);
            if (previous instanceof Map<?, ?> previousMap && value instanceof Map<?, ?> valueMap) {
                result.put(key, deepMerge((Map<String, Object>) previousMap, (Map<String, Object>) valueMap, seen));
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> rawMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> optionMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    public static List<String> unionStrings(Iterable<String> first, Iterable<String> second) {
        var result = new ArrayList<String>();
        first.forEach(value -> {
            if (!result.contains(value)) {
                result.add(value);
            }
        });
        second.forEach(value -> {
            if (!result.contains(value)) {
                result.add(value);
            }
        });
        return result;
    }
}
