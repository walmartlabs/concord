package com.walmartlabs.concord.server.process;

public class ProcessExecutorException extends Exception {

    public ProcessExecutorException(String message) {
        super(message);
    }

    public ProcessExecutorException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessExecutorException(Throwable cause) {
        super(cause);
    }
}
