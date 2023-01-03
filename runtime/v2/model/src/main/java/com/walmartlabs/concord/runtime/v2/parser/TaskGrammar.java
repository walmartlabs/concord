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
import com.walmartlabs.concord.runtime.v2.model.ImmutableTaskCallOptions;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.ExpressionGrammar.maybeExpression;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static com.walmartlabs.concord.runtime.v2.parser.LoopGrammar.loopVal;
import static com.walmartlabs.concord.runtime.v2.parser.RetryGrammar.retryVal;
import static io.takari.parc.Combinators.or;

public final class TaskGrammar {

    public static ImmutableTaskCallOptions.Builder optionsWithStepName(String stepName) {
        ImmutableTaskCallOptions.Builder result = ImmutableTaskCallOptions.builder();
        if (stepName != null) {
            result.putMeta(Constants.SEGMENT_NAME, stepName);
        }
        return result;
    }

    private static Parser<Atom, ImmutableTaskCallOptions.Builder> taskCallInOption(ImmutableTaskCallOptions.Builder o) {
        return orError(or(maybeMap.map(o::input), maybeExpression.map(o::inputExpression)), YamlValueType.TASK_CALL_IN);
    }

    private static Parser<Atom, ImmutableTaskCallOptions.Builder> taskCallOutOption(ImmutableTaskCallOptions.Builder o) {
        return orError(or(maybeMap.map(o::outExpr), maybeString.map(o::out)), YamlValueType.TASK_CALL_OUT);
    }

    private static Parser<Atom, TaskCallOptions> taskOptions(String stepName) {
        return with(() -> optionsWithStepName(stepName),
                o -> options(
                        optional("in", taskCallInOption(o)),
                        optional("out", taskCallOutOption(o)),
                        optional("meta", mapVal.map(o::putAllMeta)),
                        optional("name", stringVal.map(v -> o.putMeta(Constants.SEGMENT_NAME, v))),
                        optional("withItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, WithItems.Mode.SERIAL)))),
                        optional("parallelWithItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, WithItems.Mode.PARALLEL)))),
                        optional("loop", loopVal.map(o::loop)),
                        optional("retry", retryVal.map(o::retry)),
                        optional("error", stepsVal.map(o::errorSteps)),
                        optional("ignoreErrors", booleanVal.map(o::ignoreErrors))
                ))
                .map(ImmutableTaskCallOptions.Builder::build);
    }

    public static final Parser<Atom, TaskCall> taskFull =
            namedStep("task", YamlValueType.TASK, (stepName, a) ->
                    stringVal.bind(taskName ->
                            taskOptions(stepName).map(options -> new TaskCall(a.location, taskName, options))));

    private TaskGrammar() {
    }
}
