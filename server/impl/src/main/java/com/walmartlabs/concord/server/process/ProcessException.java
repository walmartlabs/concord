package com.walmartlabs.concord.server.process;

import javax.ws.rs.core.Response.Status;

public class ProcessException extends RuntimeException {

    private final String instanceId;
    private final Status status;

    public ProcessException(String instanceId, String message) {
        this(instanceId, message, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(String instanceId, String message, Status status) {
        super(message);
        this.instanceId = instanceId;
        this.status = status;
    }

    public ProcessException(String instanceId, String message, Throwable cause) {
        this(instanceId, message, cause, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(String instanceId, String message, Throwable cause, Status status) {
        super(message, cause);
        this.instanceId = instanceId;
        this.status = status;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Status getStatus() {
        return status;
    }
}
