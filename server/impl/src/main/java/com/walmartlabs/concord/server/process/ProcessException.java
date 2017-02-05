package com.walmartlabs.concord.server.process;

import javax.ws.rs.core.Response.Status;

public class ProcessException extends RuntimeException {

    private final Status status;

    public ProcessException(String message) {
        this(message, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(String message, Status status) {
        super(message);
        this.status = status;
    }

    public ProcessException(String message, Throwable cause) {
        this(message, cause, Status.INTERNAL_SERVER_ERROR);
    }

    public ProcessException(String message, Throwable cause, Status status) {
        super(message, cause);
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
