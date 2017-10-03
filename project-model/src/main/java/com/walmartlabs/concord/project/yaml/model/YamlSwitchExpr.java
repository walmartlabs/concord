package com.walmartlabs.concord.project.yaml.model;

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.project.yaml.KV;
import io.takari.parc.Seq;

public class YamlSwitchExpr extends YamlStep {

    private final String expr;
    private final Seq<KV<String, Seq<YamlStep>>> caseSteps;

    public YamlSwitchExpr(JsonLocation location, String expr, Seq<KV<String, Seq<YamlStep>>> options) {
        super(location);
        this.expr = expr;
        this.caseSteps = options;
    }

    public String getExpr() {
        return expr;
    }

    public Seq<KV<String, Seq<YamlStep>>> getCaseSteps() {
        return caseSteps;
    }

    @Override
    public String toString() {
        return "YamlSwitchExpr{" +
                "expr='" + expr + '\'' +
                ", caseSteps=" + caseSteps +
                '}';
    }
}
