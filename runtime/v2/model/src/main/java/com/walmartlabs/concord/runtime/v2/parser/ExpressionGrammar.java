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
import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.model.Expression;
import com.walmartlabs.concord.runtime.v2.model.ExpressionOptions;
import com.walmartlabs.concord.runtime.v2.model.ImmutableExpressionOptions;
import com.walmartlabs.concord.runtime.v2.model.Step;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static io.takari.parc.Combinators.or;
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

    public static final Parser<Atom, String> maybeExpression = expressionParser.map(expr -> (String)expr.value);
    public static final Parser<Atom, String> expressionVal = orError(expressionParser.map(expr -> (String)expr.value), YamlValueType.EXPRESSION_VAL);

    private static Parser<Atom, ImmutableExpressionOptions.Builder> exprCallOutOption(ImmutableExpressionOptions.Builder o) {
        return orError(or(maybeMap.map(o::outExpr), maybeString.map(o::out)), YamlValueType.EXPRESSION_CALL_OUT);
    }

    private static Parser<Atom, ExpressionOptions> expressionOptions(String stepName) {
        return with(() -> optionsBuilder(stepName),
                o -> options(
                        optional("error", stepsVal.map(o::errorSteps)),
                        optional("out", exprCallOutOption(o)),
                        optional("meta", mapVal.map(o::putAllMeta)),
                        optional("name", stringVal.map(v -> o.putMeta(Constants.SEGMENT_NAME, v)))
                ))
                .map(ImmutableExpressionOptions.Builder::build);
    }

    private static ImmutableExpressionOptions.Builder optionsBuilder(String stepName) {
        ImmutableExpressionOptions.Builder result = ImmutableExpressionOptions.builder();
        if (stepName != null) {
            result.putMeta(Constants.SEGMENT_NAME, stepName);
        }
        return result;
    }

    public static final Parser<Atom, Expression> exprFull =
            namedStep("expr", YamlValueType.EXPRESSION, (stepName, a) ->
                    maybeExpression.bind(expr ->
                            expressionOptions(stepName).map(options -> new Expression(a.location, expr, options))));

    // exprShort := expression
    public static final Parser<Atom, Step> exprShort =
            expressionParser.map(a -> Expression.shortForm(a.location, (String) a.value));

    private ExpressionGrammar() {
    }
}
