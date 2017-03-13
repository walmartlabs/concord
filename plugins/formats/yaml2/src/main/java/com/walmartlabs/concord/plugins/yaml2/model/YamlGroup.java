package com.walmartlabs.concord.plugins.yaml2.model;

import com.fasterxml.jackson.core.JsonLocation;
import io.takari.parc.Seq;

import java.util.Map;

public class YamlGroup extends YamlStep {

    private final Seq<YamlStep> steps;
    private final Map<String, Object> options;

    public YamlGroup(JsonLocation location, Seq<YamlStep> steps, Map<String, Object> options) {
        super(location);
        this.steps = steps;
        this.options = options;
    }

    public Seq<YamlStep> getSteps() {
        return steps;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "YamlGroup{" +
                "steps=" + steps +
                ", options=" + options +
                '}';
    }
}
