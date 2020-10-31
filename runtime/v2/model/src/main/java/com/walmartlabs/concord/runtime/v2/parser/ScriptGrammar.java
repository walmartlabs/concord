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

import com.walmartlabs.concord.runtime.v2.model.ImmutableScriptCallOptions;
import com.walmartlabs.concord.runtime.v2.model.ScriptCall;
import com.walmartlabs.concord.runtime.v2.model.ScriptCallOptions;
import com.walmartlabs.concord.runtime.v2.model.WithItems;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.RetryGrammar.retryVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.satisfyField;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.*;

public final class ScriptGrammar {

    private static final Parser<Atom, ScriptCallOptions> scriptOptions =
            with(ScriptCallOptions::builder,
                    o -> options(
                            optional("body", stringVal.map(o::body)),
                            optional("in", mapVal.map(o::input)),
                            optional("meta", mapVal.map(o::meta)),
                            optional("withItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, false)))),
                            optional("parallelWithItems", nonNullVal.map(v -> o.withItems(WithItems.of(v, true)))),
                            optional("retry", retryVal.map(o::retry)),
                            optional("error", stepsVal.map(o::errorSteps))
                    ))
                    .map(ImmutableScriptCallOptions.Builder::build);

    public static final Parser<Atom, ScriptCall> script =
            satisfyField("script", YamlValueType.SCRIPT, a ->
                    stringVal.bind(languageOrRef ->
                            scriptOptions.map(options -> new ScriptCall(a.location, languageOrRef, options))));

    private ScriptGrammar() {
    }
}
