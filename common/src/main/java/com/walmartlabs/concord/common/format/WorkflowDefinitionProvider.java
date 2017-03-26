package com.walmartlabs.concord.common.format;

import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.form.FormDefinition;

public interface WorkflowDefinitionProvider {

    ProcessDefinition getProcess(String id);

    FormDefinition getForm(String id);
}
