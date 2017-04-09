package com.walmartlabs.concord.project.model;

import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.util.Map;

public class ProjectDefinition extends Profile {

    private final Map<String, Profile> profiles;

    public ProjectDefinition(Map<String, ProcessDefinition> flows,
                             Map<String, FormDefinition> forms,
                             Map<String, Object> variables,
                             Map<String, Profile> profiles) {

        super(flows, forms, variables);
        this.profiles = profiles;
    }

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    @Override
    public String toString() {
        return "ProjectDefinition{" +
                "profiles=" + profiles +
                "} " + super.toString();
    }
}
