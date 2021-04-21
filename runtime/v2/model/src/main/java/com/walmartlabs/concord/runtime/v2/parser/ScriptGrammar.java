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
import com.walmartlabs.concord.runtime.v2.model.ImmutableScriptCallOptions;
import com.walmartlabs.concord.runtime.v2.model.ScriptCall;
import com.walmartlabs.concord.runtime.v2.model.ScriptCallOptions;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.namedStep;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;
import static com.walmartlabs.concord.runtime.v2.parser.RetryGrammar.retryVal;

public final class ScriptGrammar {

    private static Parser<Atom, ScriptCallOptions> scriptOptions(String stepName) {
        return with(() -> optionsBuilder(stepName),
                o -> options(
                        optional("body", stringVal.map(o::body)),
                        optional("in", mapVal.map(o::input)),
                        optional("meta", mapVal.map(o::meta)),
                        optional("withItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, WithItems.Mode.SERIAL)))),
                        optional("parallelWithItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, WithItems.Mode.PARALLEL)))),
                        optional("retry", retryVal.map(o::retry)),
                        optional("error", stepsVal.map(o::errorSteps))
                ))
                .map(ImmutableScriptCallOptions.Builder::build);
    }

    private static ImmutableScriptCallOptions.Builder optionsBuilder(String stepName) {
        ImmutableScriptCallOptions.Builder result = ImmutableScriptCallOptions.builder();
        if (stepName != null) {
            result.putMeta(Constants.SEGMENT_NAME, stepName);
        }
        return result;
    }

    public static final Parser<Atom, ScriptCall> script =
            namedStep("script", YamlValueType.SCRIPT, (stepName, a) ->
                stringVal.bind(languageOrRef ->
                        scriptOptions(stepName).map(options -> new ScriptCall(a.location, languageOrRef, options))));

    private ScriptGrammar() {
    }
}
