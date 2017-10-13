package com.walmartlabs.concord.plugins.misc;

import com.walmartlabs.concord.sdk.Task;
import io.takari.bpm.api.BpmnError;

import javax.inject.Named;

@Named("misc")
public class MiscTask implements Task {

    public void throwRuntimeException(String message) {
        throw new RuntimeException(message);
    }

    public void throwBpmnError(String errorRef) {
        throw new BpmnError(errorRef, new RuntimeException("A user asked for this"));
    }
}
