package com.walmartlabs.concord.project.model;

import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.util.List;
import java.util.Map;

public class ProjectDefinition extends Profile {

    private final Map<String, Profile> profiles;

    private final List<Trigger> triggers;

    public ProjectDefinition(Map<String, ProcessDefinition> flows,
                             Map<String, FormDefinition> forms,
                             Map<String, Object> variables,
                             Map<String, Profile> profiles,
                             List<Trigger> triggers) {

        super(flows, forms, variables);
        this.profiles = profiles;
        this.triggers = triggers;
    }

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public List<Trigger> getTriggers() {
        return triggers;
    }

    @Override
    public String toString() {
        return "ProjectDefinition{" +
                "profiles=" + profiles +
                "triggers=" + triggers +
                "} " + super.toString();
    }
}
