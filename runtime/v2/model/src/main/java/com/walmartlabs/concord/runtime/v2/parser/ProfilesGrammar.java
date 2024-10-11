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
import com.walmartlabs.concord.runtime.v2.model.*;
import io.takari.parc.Parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.runtime.v2.parser.ConfigurationGrammar.processCfgVal;
import static com.walmartlabs.concord.runtime.v2.parser.FlowsGrammar.flowsVal;
import static com.walmartlabs.concord.runtime.v2.parser.FormsGrammar.formsVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static io.takari.parc.Combinators.many;

public final class ProfilesGrammar {

    public static final Parser<Atom, Profile> profileDefinition =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableProfile::builder,
                            o -> options(
                                    optional("configuration", processCfgVal.map(o::configuration)),
                                    optional("flows", flowsVal.map(f -> {
                                        Map<String, List<Step>> flows = new LinkedHashMap<>(f.size());
                                        for (Map.Entry<String, Flow> e : f.entrySet()) {
                                            flows.put(e.getKey(), e.getValue().steps());
                                        }
                                        return o.flows(flows)
                                                .flowsDefinition(f);
                                    })),
                                    optional("forms", formsVal.map(o::forms))))
                            .map(ImmutableProfile.Builder::build));

    private static final Parser<Atom, KV<String, Profile>> profile =
            satisfyAnyField(YamlValueType.PROFILE, f -> profileDefinition.map(s -> new KV<>(f.name, s)));

    private static final Parser<Atom, Map<String, Profile>> profiles =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    many(profile).map(GrammarV2::toMap));

    public static final Parser<Atom, Map<String, Profile>> profilesVal =
            orError(profiles, YamlValueType.PROFILES);

    private ProfilesGrammar() {
    }
}
