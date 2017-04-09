package com.walmartlabs.concord.project.model;

import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class ProjectDefinitionUtils {

    public static ProcessDefinition getFlow(ProjectDefinition project, Collection<String> activeProfiles, String flowName) {
        Map<String, ProcessDefinition> flows = project.getFlows();
        Map<String, Profile> profiles = project.getProfiles();

        Map<String, ProcessDefinition> view = overlay(flows, profiles, activeProfiles, p -> p.getFlows());
        return view.get(flowName);
    }

    public static FormDefinition getForm(ProjectDefinition project, Collection<String> activeProfiles, String formName) {
        Map<String, FormDefinition> forms = project.getForms();
        Map<String, Profile> profiles = project.getProfiles();

        Map<String, FormDefinition> view = overlay(forms, profiles, activeProfiles, p -> p.getForms());
        return view.get(formName);
    }

    public static Map<String, Object> getVariables(ProjectDefinition project, Collection<String> activeProfiles) {
        Map<String, Object> variables = project.getVariables();
        Map<String, Profile> profiles = project.getProfiles();

        Map<String, Object> view = overlay(variables, profiles, activeProfiles, p -> p.getVariables());
        return view;
    }

    private static <T> Map<String, T> overlay(Map<String, T> initial,
                                              Map<String, Profile> profiles,
                                              Collection<String> activeProfiles,
                                              Function<Profile, Map<String, T>> selector) {

        Map<String, T> view = new HashMap<>(initial != null ? initial : Collections.emptyMap());
        if (profiles != null && activeProfiles != null) {
            for (String n : activeProfiles) {
                Profile p = profiles.get(n);
                if (p == null) {
                    continue;
                }

                Map<String, T> overlays = selector.apply(p);
                if (overlays != null) {
                    view.putAll(overlays);
                }
            }
        }
        return view;
    }

    private ProjectDefinitionUtils() {
    }
}
