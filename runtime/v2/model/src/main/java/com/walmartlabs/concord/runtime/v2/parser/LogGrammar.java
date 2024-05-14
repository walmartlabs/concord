package com.walmartlabs.concord.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.namedStep;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.namedOptions;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.anyVal;

public final class LogGrammar {

    public static final Parser<Atom, TaskCall> logStep =
            namedStep("log", YamlValueType.TASK, (stepName, a) ->
                    anyVal.bind(msg ->
                            namedOptions.map(options -> new TaskCall(a.location, "log", TaskGrammar.optionsWithStepName(stepName)
                                    .putInput("msg", msg)
                                    .putAllMeta(options.meta())
                                    .build()))));

    public static final Parser<Atom, TaskCall> logYamlStep =
            namedStep("logYaml", YamlValueType.TASK, (stepName, a) ->
                    anyVal.bind(msg ->
                            namedOptions.map(options -> new TaskCall(a.location, "log", TaskGrammar.optionsWithStepName(stepName)
                                    .putInput("msg", msg)
                                    .putInput("format", "yaml")
                                    .putAllMeta(options.meta())
                                    .build()))));

    private LogGrammar() {
    }
}
