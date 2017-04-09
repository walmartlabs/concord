package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;

public class YamlCall extends YamlStep {

    private final String proc;

    public YamlCall(JsonLocation location, String proc) {
        super(location);
        this.proc = proc;
    }

    public String getProc() {
        return proc;
    }

    @Override
    public String toString() {
        return "YamlCall{" +
                "proc='" + proc + '\'' +
                '}';
    }
}
