package com.walmartlabs.concord.plugins.yaml2.model;

import com.fasterxml.jackson.core.JsonLocation;

public class YamlTaskShortStep extends YamlStep {

    private final String key;
    private final Object arg;

    public YamlTaskShortStep(JsonLocation location, String key, Object arg) {
        super(location);
        this.key = key;
        this.arg = arg;
    }

    public String getKey() {
        return key;
    }

    public Object getArg() {
        return arg;
    }

    @Override
    public String toString() {
        return "YamlTaskShortStep{" +
                "key='" + key + '\'' +
                ", arg=" + arg +
                '}';
    }
}
