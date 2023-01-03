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
import com.walmartlabs.concord.runtime.v2.model.Step;
import io.takari.parc.Parser;
import io.takari.parc.Seq;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static io.takari.parc.Combinators.many;
import static io.takari.parc.Combinators.many1;

public final class FlowsGrammar {

    private static final Parser<Atom, List<Step>> steps =
            betweenTokens(JsonToken.START_ARRAY, JsonToken.END_ARRAY,
                    many1(GrammarV2.getProcessStep()).map(Seq::toList));

    private static final Parser<Atom, KV<String, List<Step>>> flow =
            satisfyAnyField(YamlValueType.FLOW, f -> steps.map(s -> new KV<>(f.name, s)));

    private static Map<String, List<Step>> toMap(Seq<KV<String, List<Step>>> values) {
        Map<String, List<Step>> m = new LinkedHashMap<>();
        values.stream().forEach(kv -> m.put(kv.getKey(), kv.getValue()));
        return m;
    }

    private static final Parser<Atom, Map<String, List<Step>>> flows =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    many(flow).map(FlowsGrammar::toMap));

    public static final Parser<Atom, Map<String, List<Step>>> flowsVal =
            orError(flows, YamlValueType.FLOWS);

    private FlowsGrammar() {
    }
}
