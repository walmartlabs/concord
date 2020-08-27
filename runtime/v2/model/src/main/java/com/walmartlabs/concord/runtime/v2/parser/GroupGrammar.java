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

import com.walmartlabs.concord.runtime.v2.model.*;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.satisfyField;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.choice;

public final class GroupGrammar {

    private static final Parser<Atom, GroupOfStepsOptions> groupOptions =
            with(GroupOfStepsOptions::builder,
                    o -> options(
                            optional("error", stepsVal.map(o::errorSteps)),
                            optional("withItems", nonNullVal.map(v -> o.withItems(WithItems.of(v)))),
                            optional("meta", mapVal.map(o::meta))
                    ))
                    .map(ImmutableGroupOfStepsOptions.Builder::build);

    private static Parser<Atom, GroupOfSteps> groupDef(Atom a) {
        return stepsVal.bind(steps -> groupOptions.map(options -> new GroupOfSteps(a.location, steps, options)));
    }

    public static final Parser<Atom, GroupOfSteps> groupAsTry =
            satisfyField("try", YamlValueType.TRY, GroupGrammar::groupDef);

    public static final Parser<Atom, GroupOfSteps> groupAsBlock =
            satisfyField("block", YamlValueType.BLOCK, GroupGrammar::groupDef);

    public static final Parser<Atom, Step> group = choice(groupAsTry, groupAsBlock);

    private GroupGrammar() {
    }
}
