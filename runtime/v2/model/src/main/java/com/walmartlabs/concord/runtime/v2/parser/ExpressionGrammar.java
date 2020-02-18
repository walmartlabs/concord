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
import com.walmartlabs.concord.runtime.v2.model.Expression;
import com.walmartlabs.concord.runtime.v2.model.ExpressionOptions;
import com.walmartlabs.concord.runtime.v2.model.ImmutableExpressionOptions;
import com.walmartlabs.concord.runtime.v2.model.Step;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.satisfyField;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.satisfy;

public final class ExpressionGrammar {

    // expression := VALUE_STRING ${.*}
    private static final Parser<Atom, Atom> expressionParser = satisfy((Atom a) -> {
        if (a.token != JsonToken.VALUE_STRING) {
            return false;
        }

        String s = (String) a.value;
        return s != null && s.startsWith("${") && s.endsWith("}");
    });

    public static final Parser<Atom, String> expression = expressionParser.map(expr -> (String)expr.value);

    private static final Parser<Atom, ExpressionOptions> expressionOptions =
            with(ExpressionOptions::builder,
                    o -> options(
                            optional("error", stepsVal.map(o::errorSteps)),
                            optional("out", stringVal.map(o::out)),
                            optional("meta", mapVal.map(o::meta))
                    ))
                    .map(ImmutableExpressionOptions.Builder::build);

    public static final Parser<Atom, Expression> exprFull =
            satisfyField("expr", YamlValueType.EXPRESSION, a ->
                    expression.bind(expr ->
                            expressionOptions.map(options -> new Expression(a.location, expr, options))));

    // exprShort := expression
    public static final Parser<Atom, Step> exprShort =
            expressionParser.map(a -> Expression.shortForm(a.location, (String) a.value));

    private ExpressionGrammar() {
    }
}
