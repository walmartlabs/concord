package com.walmartlabs.concord.agent;

public class ExecutionException extends Exception {

    public ExecutionException(String message) {
        super(message);
    }

    public ExecutionException(String message, Object[]... args) {
        super(String.format(message, (Object) args));
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutionException(String message, Throwable cause, Object[]... args) {
        super(String.format(message, (Object) args), cause);
    }
}
