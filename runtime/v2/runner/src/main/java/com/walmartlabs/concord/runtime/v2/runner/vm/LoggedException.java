package com.walmartlabs.concord.runtime.v2.runner.vm;

public class LoggedException extends RuntimeException {

    public LoggedException(Exception cause) {
        super(cause);
    }

    @Override
    public Exception getCause() {
        return (Exception) super.getCause();
    }

    @Override
    public String toString() {
        return getCause().toString();
    }
}
