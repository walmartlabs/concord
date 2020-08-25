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

import com.walmartlabs.concord.runtime.v2.Constants;
import com.walmartlabs.concord.runtime.v2.model.ImmutableTaskCallOptions;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.CommonGrammar.retryVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.namedStep;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;

public final class TaskGrammar {

    private static ImmutableTaskCallOptions.Builder optionsBuilder(String stepName) {
        ImmutableTaskCallOptions.Builder result = ImmutableTaskCallOptions.builder();
        if (stepName != null) {
            result.putMeta(Constants.SEGMENT_NAME, stepName);
        }
        return result;
    }

    private static Parser<Atom, TaskCallOptions> taskOptions(String stepName) {
        return with(() -> optionsBuilder(stepName),
                o -> options(
                        optional("in", mapVal.map(o::input)),
                        optional("out", stringVal.map(o::out)),
                        optional("meta", mapVal.map(o::putAllMeta)),
                        optional("withItems", nonNullVal.map(v -> o.withItems(WithItems.of(v)))),
                        optional("retry", retryVal.map(o::retry)),
                        optional("error", stepsVal.map(o::errorSteps))
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
