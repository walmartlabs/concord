package com.walmartlabs.concord.common.format;

import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

import java.io.Serializable;
import java.util.Map;

public class WorkflowDefinition implements Serializable {

    private final String source;
    private final Map<String, ProcessDefinition> processes;
    private final Map<String, FormDefinition> forms;

    public WorkflowDefinition(String source, Map<String, ProcessDefinition> processes, Map<String, FormDefinition> forms) {
        this.source = source;
        this.processes = processes;
        this.forms = forms;
    }

    public String getSource() {
        return source;
    }

    public Map<String, ProcessDefinition> getProcesses() {
        return processes;
    }

    public Map<String, FormDefinition> getForms() {
        return forms;
    }
}
