package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

public class YamlReturn extends YamlStep {

    private final String errorCode;

    public YamlReturn(JsonLocation location) {
        this(location, null);
    }

    public YamlReturn(JsonLocation location, String errorCode) {
        super(location);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "YamlReturn{" +
                "errorCode='" + errorCode + '\'' +
                '}';
    }
}
