package com.walmartlabs.concord.server.process.state;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class ProcessMetadataManager {

    public static final String ON_FAILURE_MARKER_PATH = ".concord/meta/has_on_failure";
    public static final String ON_CANCEL_MARKER_PATH = ".concord/meta/has_on_cancel";

    private final ProcessStateManager stateManager;

    @Inject
    public ProcessMetadataManager(ProcessStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void addOnFailureMarker(UUID instanceId) {
        stateManager.insert(instanceId, ON_FAILURE_MARKER_PATH, "true".getBytes());
    }

    public void deleteOnFailureMarker(UUID instanceId) {
        stateManager.delete(instanceId, ON_FAILURE_MARKER_PATH);
    }

    public void addOnCancelMarker(UUID instanceId) {
        stateManager.insert(instanceId, ON_CANCEL_MARKER_PATH, "true".getBytes());
    }

    public void deleteOnCancelMarker(UUID instanceId) {
        stateManager.delete(instanceId, ON_CANCEL_MARKER_PATH);
    }
}
