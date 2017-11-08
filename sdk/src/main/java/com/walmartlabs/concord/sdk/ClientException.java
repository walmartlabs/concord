package com.walmartlabs.concord.sdk;

public class ClientException extends Exception {

    private final String instanceId;

    public ClientException(String message) {
        this(null, message, null);
    }

    public ClientException(String message, Throwable cause) {
        this(null, message, cause);
    }

    public ClientException(String instanceId, String message, Throwable cause) {
        super(message, cause);
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }
}
