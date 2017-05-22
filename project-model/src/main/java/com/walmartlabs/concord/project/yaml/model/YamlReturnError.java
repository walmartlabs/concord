package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

public class YamlReturnError extends YamlStep {

    private final String errorCode;

    public YamlReturnError(JsonLocation location) {
        this(location, null);
    }

    public YamlReturnError(JsonLocation location, String errorCode) {
        super(location);

        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "YamlReturnError{" +
                "errorCode='" + errorCode + '\'' +
                '}';
    }
}
