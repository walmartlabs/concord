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

import com.walmartlabs.concord.runtime.v2.model.ImmutableParallelBlockOptions;
import com.walmartlabs.concord.runtime.v2.model.ParallelBlock;
import com.walmartlabs.concord.runtime.v2.model.ParallelBlockOptions;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.or;

public final class ParallelGrammar {

    private static Parser<Atom, ImmutableParallelBlockOptions.Builder> parallelOutOption(ImmutableParallelBlockOptions.Builder o) {
        return orError(or(maybeMap.map(o::outExpr), or(maybeString.map(o::addOut), maybeStringArray.map(o::out))), YamlValueType.PARALLEL_BLOCK_OUT);
    }

    private static final Parser<Atom, ParallelBlockOptions> parallelOptions =
            with(ParallelBlockOptions::builder,
                    o -> options(
                            optional("out", parallelOutOption(o)),
                            optional("meta", mapVal.map(o::meta))
                    ))
                    .map(ImmutableParallelBlockOptions.Builder::build);

    public static final Parser<Atom, ParallelBlock> parallelBlock =
            satisfyField("parallel", YamlValueType.PARALLEL, a ->
                    stepsVal.bind(steps -> parallelOptions.map(options -> new ParallelBlock(a.location, steps, options))));

    private ParallelGrammar() {
    }
}
