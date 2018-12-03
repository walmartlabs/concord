package com.walmartlabs.concord.project.yaml;

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

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.project.model.Profile;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.project.yaml.converter.StepConverter;
import com.walmartlabs.concord.project.yaml.model.*;
import com.walmartlabs.concord.sdk.Constants;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceMap;
import io.takari.bpm.model.form.FormDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YamlProjectConverter {

    private static final String[] TRIGGER_CONFIG_KEYS = {
            Constants.Trigger.USE_INITIATOR,
            Constants.Trigger.USE_EVENT_COMMIT_ID,
            Constants.Request.ENTRY_POINT_KEY
    };


    public static ProjectDefinition convert(YamlProject project) throws YamlConverterException {
        Map<String, ProcessDefinition> flows = convertFlows(project.getFlows());
        Map<String, FormDefinition> forms = convertForms(project.getForms());
        Map<String, Object> cfg = project.getConfiguration();
        Map<String, Profile> profiles = convertProfiles(project.getProfiles());
        List<Trigger> triggers = convertTriggers(project.getTriggers());
        return new ProjectDefinition(flows, forms, cfg, profiles, triggers);
    }

    @SuppressWarnings("unchecked")
    private static List<Trigger> convertTriggers(List<YamlTrigger> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return null;
        }

        List<Trigger> result = new ArrayList<>();
        for (YamlTrigger t : triggers) {
            String name = t.getName();

            Map<String, Object> opts = (Map<String, Object>) StepConverter.deepConvert(t.getOptions());

            List<String> activeProfiles = (List<String>) opts.remove(Constants.Request.ACTIVE_PROFILES_KEY);
            Map<String, Object> arguments = (Map<String, Object>) opts.remove(Constants.Request.ARGUMENTS_KEY);

            Map<String, Object> cfg = new HashMap<>();
            for (String key : TRIGGER_CONFIG_KEYS) {
                cfg.put(key, opts.remove(key));
            }

            JsonLocation l = t.getLocation();
            SourceMap sourceMap = new SourceMap(SourceMap.Significance.HIGH,
                    String.valueOf(l.getSourceRef()),
                    l.getLineNr(),
                    l.getColumnNr(),
                    "Trigger: " + name);

            result.add(new Trigger(name, activeProfiles, arguments, opts, cfg, sourceMap));
        }

        return result;
    }

    public static Profile convert(YamlProfile profile) throws YamlConverterException {
        return convertProfile(profile);
    }

    private static Map<String, Profile> convertProfiles(Map<String, YamlProfile> profiles) throws YamlConverterException {
        if (profiles == null) {
            return null;
        }

        Map<String, Profile> m = new HashMap<>(profiles.size());
        for (Map.Entry<String, YamlProfile> e : profiles.entrySet()) {
            String k = e.getKey();
            m.put(k, convertProfile(e.getValue()));
        }

        return m;
    }

    private static Profile convertProfile(YamlProfile profile) throws YamlConverterException {
        Map<String, ProcessDefinition> flows = convertFlows(profile.getFlows());
        Map<String, FormDefinition> forms = convertForms(profile.getForms());
        Map<String, Object> cfg = profile.getConfiguration();
        return new Profile(flows, forms, cfg);
    }

    private static Map<String, ProcessDefinition> convertFlows(Map<String, List<YamlStep>> flows) throws YamlConverterException {
        if (flows == null) {
            return null;
        }

        Map<String, ProcessDefinition> m = new HashMap<>(flows.size());
        for (Map.Entry<String, List<YamlStep>> e : flows.entrySet()) {
            String k = e.getKey();
            m.put(k, YamlProcessConverter.convert(k, e.getValue()));
        }

        return m;
    }

    private static Map<String, FormDefinition> convertForms(Map<String, List<YamlFormField>> forms) throws YamlConverterException {
        if (forms == null) {
            return null;
        }

        Map<String, FormDefinition> m = new HashMap<>(forms.size());
        for (Map.Entry<String, List<YamlFormField>> e : forms.entrySet()) {
            String k = e.getKey();
            m.put(k, YamlFormConverter.convert(k, e.getValue()));
        }

        return m;
    }

    private YamlProjectConverter() {
    }
}
