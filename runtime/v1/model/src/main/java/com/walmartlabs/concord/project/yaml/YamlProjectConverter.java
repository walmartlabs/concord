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

import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.project.model.Profile;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.model.Resources;
import com.walmartlabs.concord.project.model.Trigger;
import com.walmartlabs.concord.project.yaml.model.YamlFormField;
import com.walmartlabs.concord.project.yaml.model.YamlProfile;
import com.walmartlabs.concord.project.yaml.model.YamlProject;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.walmartlabs.concord.project.yaml.YamlImportConverter.convertImports;

public final class YamlProjectConverter {

    public static ProjectDefinition convert(YamlProject project) throws YamlConverterException {
        Map<String, ProcessDefinition> flows = convertFlows(project.getFlows());
        Set<String> publicFlows = project.getPublicFlows();
        Map<String, FormDefinition> forms = convertForms(project.getForms());
        Map<String, Object> cfg = project.getConfiguration();
        Map<String, Profile> profiles = convertProfiles(project.getProfiles());
        List<Trigger> triggers = YamlTriggersConverter.convert(project.getTriggers());
        Imports imports = convertImports(project.getImports());
        Resources resources = convertResources(project.getResources());
        return new ProjectDefinition(flows, publicFlows, forms, cfg, profiles, triggers, imports, resources);
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
        Set<String> publicFlows = profile.getPublicFlows();
        Map<String, FormDefinition> forms = convertForms(profile.getForms());
        Map<String, Object> cfg = profile.getConfiguration();
        return new Profile(flows, publicFlows, forms, cfg);
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

    private static Resources convertResources(Map<String, Object> resources) {
        if (resources == null) {
            return null;
        }

        return YamlResourcesConverter.parse(resources);
    }

    private YamlProjectConverter() {
    }
}
