package com.walmartlabs.concord.project.yaml.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.core.JsonLocation;
import io.takari.parc.Seq;

public class YamlIfExpr extends YamlStep {

    private static final long serialVersionUID = 1L;

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
