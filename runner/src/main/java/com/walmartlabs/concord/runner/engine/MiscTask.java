package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;

import javax.inject.Named;

@Named("misc")
public class MiscTask implements Task {

    public void throwBpmnError(String errorRef) {
        throw new BpmnError(errorRef, new RuntimeException("A user asked for this"));
    }
}
