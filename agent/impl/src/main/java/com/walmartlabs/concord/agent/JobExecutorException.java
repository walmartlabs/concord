package com.walmartlabs.concord.agent;

public class JobExecutorException extends RuntimeException {

    public JobExecutorException(String message) {
        super(message);
    }

    public JobExecutorException(String message, Throwable cause) {
        super(message, cause);
    }
}
