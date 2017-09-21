package com.walmartlabs.concord.project.model;

import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.io.Serializable;
import java.util.Map;

public class Profile implements Serializable {

    private final Map<String, ProcessDefinition> flows;
    private final Map<String, FormDefinition> forms;
    private final Map<String, Object> configuration;

    public Profile(Map<String, ProcessDefinition> flows,
                   Map<String, FormDefinition> forms,
                   Map<String, Object> configuration) {

        this.flows = flows;
        this.forms = forms;
        this.configuration = configuration;
    }

    public Map<String, ProcessDefinition> getFlows() {
        return flows;
    }

    public Map<String, FormDefinition> getForms() {
        return forms;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public String toString() {
        return "Profile{" +
                "flows=" + flows +
                ", forms=" + forms +
                ", variables=" + configuration +
                '}';
    }
}
