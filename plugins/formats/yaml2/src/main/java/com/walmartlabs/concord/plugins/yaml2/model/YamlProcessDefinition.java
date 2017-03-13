package com.walmartlabs.concord.plugins.yaml2.model;

import io.takari.parc.Seq;

public class YamlProcessDefinition implements YamlDefinition {

    private final String name;
    private final Seq<YamlStep> steps;

    public YamlProcessDefinition(String name, Seq<YamlStep> steps) {
        this.name = name;
        this.steps = steps;
    }

    public String getName() {
        return name;
    }

    public Seq<YamlStep> getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return "YamlProcessDefinition{" +
                "name='" + name + '\'' +
                ", steps=" + steps +
                '}';
    }
}
