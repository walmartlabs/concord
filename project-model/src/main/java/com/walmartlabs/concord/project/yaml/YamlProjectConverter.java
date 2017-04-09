package com.walmartlabs.concord.project.yaml;

import com.walmartlabs.concord.project.model.Profile;
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.yaml.model.YamlFormField;
import com.walmartlabs.concord.project.yaml.model.YamlProfile;
import com.walmartlabs.concord.project.yaml.model.YamlProject;
import com.walmartlabs.concord.project.yaml.model.YamlStep;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YamlProjectConverter {

    public static ProjectDefinition convert(YamlProject project) throws YamlConverterException {
        Map<String, ProcessDefinition> flows = convertFlows(project.getFlows());
        Map<String, FormDefinition> forms = convertForms(project.getForms());
        Map<String, Object> variables = project.getVariables();
        Map<String, Profile> profiles = convertProfiles(project.getProfiles());
        return new ProjectDefinition(flows, forms, variables, profiles);
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
        Map<String, Object> variables = profile.getVariables();
        return new Profile(flows, forms, variables);
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
