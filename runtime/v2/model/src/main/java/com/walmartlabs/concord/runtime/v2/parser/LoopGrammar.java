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

import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.runtime.v2.model.ImmutableLoop;
import com.walmartlabs.concord.runtime.v2.model.Loop;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.maybeExpression;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.or;

public final class LoopGrammar {

    private static Parser<Atom, ImmutableLoop.Builder> parallelism(ImmutableLoop.Builder o) {
        return orError(or(maybeInt.map(v -> o.putOptions("parallelism", v)), maybeExpression.map(v -> o.putOptions("parallelism", v))), YamlValueType.LOOP_PARALLELISM);
    }

    private static final Parser<Atom, Loop> loop =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(Loop::builder,
                            o -> options(
                                    mandatory("items", nonNullVal.map(o::items)),
                                    optional("mode", enumVal(Loop.Mode.class, String::equalsIgnoreCase).map(o::mode)),
                                    optional("parallelism", parallelism(o))
                            ))
                            .map(ImmutableLoop.Builder::build));

    public static final Parser<Atom, Loop> loopVal =
            orError(loop, YamlValueType.LOOP);

    private LoopGrammar() {
    }
}
