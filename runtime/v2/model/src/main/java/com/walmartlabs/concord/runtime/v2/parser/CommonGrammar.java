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

import com.fasterxml.jackson.core.JsonToken;
import com.walmartlabs.concord.runtime.v2.model.ImmutableRetry;
import com.walmartlabs.concord.runtime.v2.model.Retry;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.expression;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.or;

public final class CommonGrammar {

    private static final Parser<Atom, Retry> retry =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(Retry::builder,
                            o -> options(
                                    optional("times", orError(or(maybeInt.map(o::times), expression.map(o::timesExpression)), YamlValueType.RETRY_TIMES)),
                                    optional("delay", orError(or(maybeInt.map(o::delay), expression.map(o::delayExpression)), YamlValueType.RETRY_DELAY)),
                                    optional("in", mapVal.map(o::input))
                            ))
                            .map(ImmutableRetry.Builder::build));

    public static final Parser<Atom, Retry> retryVal =
            orError(retry, YamlValueType.RETRY);

    private CommonGrammar() {
    }
}
