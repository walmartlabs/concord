package com.walmartlabs.concord.server.template;

public class TemplateException extends Exception {

    public TemplateException(String format, Object... args) {
        super(String.format(format, args));
    }

    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
