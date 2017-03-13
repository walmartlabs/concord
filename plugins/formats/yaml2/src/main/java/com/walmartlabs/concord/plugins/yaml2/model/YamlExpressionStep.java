package com.walmartlabs.concord.plugins.yaml2.model;

import com.fasterxml.jackson.core.JsonLocation;

import java.util.Collections;
import java.util.Map;

public class YamlExpressionStep extends YamlStep {

    private final String expr;
    private final Map<String, Object> options;

    public YamlExpressionStep(JsonLocation location, String expr) {
        this(location, expr, Collections.emptyMap());
    }

    public YamlExpressionStep(JsonLocation location, String expr, Map<String, Object> options) {
        super(location);
        this.expr = expr;
        this.options = options;
    }

    public String getExpr() {
        return expr;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @Override
    public String toString() {
        return "YamlExpressionStep{" +
                "expr='" + expr + '\'' +
                ", options=" + options +
                '}';
    }
}
