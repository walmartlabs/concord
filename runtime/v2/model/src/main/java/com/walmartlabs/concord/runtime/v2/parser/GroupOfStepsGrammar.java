package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.model.*;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.namedStep;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static com.walmartlabs.concord.runtime.v2.parser.LoopGrammar.loopVal;
import static io.takari.parc.Combinators.choice;

public final class GroupOfStepsGrammar {

    private static ImmutableGroupOfStepsOptions.Builder optionsWithStepName(String stepName) {
        ImmutableGroupOfStepsOptions.Builder result = GroupOfStepsOptions.builder();
        if (stepName != null) {
            result.putMeta(Constants.SEGMENT_NAME, stepName);
        }
        return result;
    }

    private static Parser<Atom, GroupOfStepsOptions> groupOptions(String stepName) {
        return with(() -> optionsWithStepName(stepName),
                o -> options(
                        optional("out", stringOrArrayVal.map(o::out)),
                        optional("error", stepsVal.map(o::errorSteps)),
                        optional("withItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, WithItems.Mode.SERIAL)))),
                        optional("parallelWithItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, WithItems.Mode.PARALLEL)))),
                        optional("loop", loopVal.map(o::loop)),
                        optional("meta", mapVal.map(o::putAllMeta)),
                        optional("name", stringVal.map(v -> o.putMeta(Constants.SEGMENT_NAME, v)))
                ))
                .map(ImmutableGroupOfStepsOptions.Builder::build);
    }

    private static Parser<Atom, GroupOfSteps> groupDef(String stepName, Atom a) {
        return stepsVal.bind(steps -> groupOptions(stepName).map(options -> new GroupOfSteps(a.location, steps, options)));
    }

    public static final Parser<Atom, GroupOfSteps> groupAsTry =
            namedStep("try", YamlValueType.TRY, GroupOfStepsGrammar::groupDef);

    public static final Parser<Atom, GroupOfSteps> groupAsBlock =
            namedStep("block", YamlValueType.BLOCK, GroupOfStepsGrammar::groupDef);

    public static final Parser<Atom, Step> group = choice(groupAsTry, groupAsBlock);

    private GroupOfStepsGrammar() {
    }
}
