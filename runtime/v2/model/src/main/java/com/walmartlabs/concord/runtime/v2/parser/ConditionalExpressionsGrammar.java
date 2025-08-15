package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.exception.YamlProcessingException;
import com.walmartlabs.concord.runtime.v2.model.IfStep;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.model.SwitchStep;
import io.takari.parc.Parser;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.expressionVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.satisfyField;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.mapVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.stepsVal;

public final class ConditionalExpressionsGrammar {

    public static final Parser<Atom, SwitchStep> switchExpr =
            satisfyField("switch", YamlValueType.SWITCH, a -> expressionVal.bind(expr ->
                    with(SwitchStepBuilder::builder,
                            o -> options(
                                    optional("default", stepsVal.map(o::defaultSteps)),
                                    any((atom, s) -> stepsVal.map(steps -> o.caseStep(s, steps)))))
                            .map(o -> o.build(a.location, expr))
            ));

    public static final Parser<Atom, IfStep> ifExpr =
            satisfyField("if", YamlValueType.IF, a -> expressionVal.bind(expr ->
                    with(IfStepBuilder::builder,
                        o -> options(
                                mandatory("then", stepsVal.map(o::thenSteps)),
                                optional("else", stepsVal.map(o::elseSteps)),
                                optional("meta", mapVal.map(v -> o.options(SimpleOptions.of(v))))))
                        .map(o -> o.build(a.location, expr))
                    ));

    static class IfStepBuilder {

        private List<Step> thenSteps;
        private List<Step> elseSteps;
        private SimpleOptions options;

        public IfStepBuilder thenSteps(List<Step> thenSteps) {
            this.thenSteps = thenSteps;
            return this;
        }

        public IfStepBuilder elseSteps(List<Step> elseSteps) {
            this.elseSteps = elseSteps;
            return this;
        }

        public IfStepBuilder options(SimpleOptions options) {
            this.options = options;
            return this;
        }

        public IfStep build(Location location, String expression) {
            return new IfStep(location, expression, thenSteps, elseSteps, options);
        }

        static IfStepBuilder builder() {
            return new IfStepBuilder();
        }
    }

    static class SwitchStepBuilder {

        private final List<Map.Entry<String, List<Step>>> caseSteps = new ArrayList<>();
        private List<Step> defaultSteps;
        private SimpleOptions options;

        public SwitchStepBuilder caseStep(String caseLabel, List<Step> caseSteps) {
            this.caseSteps.add(new AbstractMap.SimpleEntry<>(caseLabel, caseSteps));
            return this;
        }

        public SwitchStepBuilder defaultSteps(List<Step> defaultSteps) {
            this.defaultSteps = defaultSteps;
            return this;
        }

        public SwitchStepBuilder options(SimpleOptions options) {
            this.options = options;
            return this;
        }

        public SwitchStep build(Location location, String expression) {
            if (defaultSteps == null && caseSteps.isEmpty()) {
                throw new YamlProcessingException(location, "No branch labels defined");
            }
            return new SwitchStep(location, expression, caseSteps, defaultSteps, options);
        }

        static SwitchStepBuilder builder() {
            return new SwitchStepBuilder();
        }
    }

    private ConditionalExpressionsGrammar() {
    }
}
