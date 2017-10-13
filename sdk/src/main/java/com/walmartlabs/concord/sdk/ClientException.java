package com.walmartlabs.concord.sdk;

public class ClientException extends Exception {

    public ClientException(String message) {
        super(message);
    }

    public ClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
