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
import com.walmartlabs.concord.runtime.v2.model.ImmutableTaskCallOptions;
import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.CommonGrammar.retryVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;

public final class TaskGrammar {

    private static final Parser<Atom, TaskCallOptions> taskOptions =
            with(TaskCallOptions::builder,
                    o -> options(
                            optional("in", mapVal.map(o::input)),
                            optional("out", stringVal.map(o::out)),
                            optional("meta", mapVal.map(o::meta)),
                            optional("withItems", nonNullVal.map(v -> o.withItems(WithItems.of(v)))),
                            optional("retry", retryVal.map(o::retry)),
                            optional("error", stepsVal.map(o::errorSteps))
                    ))
                    .map(ImmutableTaskCallOptions.Builder::build);

    private static final Parser<Atom, TaskCallOptions> taskShortOptions =
            with(TaskCallOptions::builder,
                    o -> options(
                            optional("meta", mapVal.map(o::meta))
                    ))
                    .map(ImmutableTaskCallOptions.Builder::build);

    public static final Parser<Atom, TaskCall> taskFull =
            satisfyField("task", YamlValueType.TASK, a ->
                    stringVal.bind(taskName ->
                            taskOptions.map(options -> new TaskCall(a.location, taskName, options))));

    public static final Parser<Atom, TaskCall> taskShort =
            satisfyToken(JsonToken.FIELD_NAME).bind(a ->
                    anyVal.bind(arg ->
                            taskShortOptions.map(options -> TaskCall.singleArgCall(a.location, options, a.name, arg))));

    private TaskGrammar() {
    }
}
