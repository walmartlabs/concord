package com.walmartlabs.concord.project.model;

import java.io.Serializable;
import java.util.Map;

public class Trigger implements Serializable {

    private final String name;

    private final String entryPoint;

    private final Map<String, Object> arguments;

    private final Map<String, Object> params;

    public Trigger(String name, String entryPoint, Map<String, Object> arguments, Map<String, Object> params) {
        this.name = name;
        this.entryPoint = entryPoint;
        this.arguments = arguments;
        this.params = params;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "Trigger{" +
                "name='" + name + '\'' +
                ", entryPoint='" + entryPoint + '\'' +
                ", arguments=" + arguments +
                ", params=" + params +
                '}';
    }
}
