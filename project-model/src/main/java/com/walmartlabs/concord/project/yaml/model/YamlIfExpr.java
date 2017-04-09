package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;
import io.takari.parc.Seq;

public class YamlIfExpr extends YamlStep {

    private final String expr;
    private final Seq<YamlStep> thenSteps;
    private final Seq<YamlStep> elseSteps;

    public YamlIfExpr(JsonLocation location, String expr, Seq<YamlStep> thenSteps, Seq<YamlStep> elseSteps) {
        super(location);
        this.expr = expr;
        this.thenSteps = thenSteps;
        this.elseSteps = elseSteps;
    }

    public String getExpr() {
        return expr;
    }

    public Seq<YamlStep> getThenSteps() {
        return thenSteps;
    }

    public Seq<YamlStep> getElseSteps() {
        return elseSteps;
    }

    @Override
    public String toString() {
        return "YamlIfExpr{" +
                "expr='" + expr + '\'' +
                ", thenSteps=" + thenSteps +
                ", elseSteps=" + elseSteps +
                '}';
    }
}
