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
import com.walmartlabs.concord.runtime.v2.model.ImmutableProfile;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.model.Profile;
import io.takari.parc.Parser;
import io.takari.parc.Result;

import java.io.Serializable;
import java.util.Map;

import static com.walmartlabs.concord.runtime.v2.parser.ConfigurationGrammar.processCfgVal;
import static com.walmartlabs.concord.runtime.v2.parser.FlowsGrammar.flowsVal;
import static com.walmartlabs.concord.runtime.v2.parser.FormsGrammar.formsVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.mapVal;
import static io.takari.parc.Combinators.many;

public final class ProfilesGrammar {

    public static final Parser<Atom, Profile> profileDefinition =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableProfile::builder,
                            o -> options(
                                    optional("configuration", profileCfgVal(o).map(o::configuration)),
                                    optional("flows", flowsVal.map(o::flows)),
                                    optional("forms", formsVal.map(o::forms))))
                            .map(ImmutableProfile.Builder::build));

    /**
     * Parses the profile's {@code configuration} block into a {@link ProcessDefinitionConfiguration},
     * additionally keeping the block's original YAML shape in
     * {@link Profile#rawConfiguration()}.
     */
    private static Parser<Atom, ProcessDefinitionConfiguration> profileCfgVal(ImmutableProfile.Builder o) {
        return in -> {
            // parse the typed configuration first, so that invalid values are reported in
            // terms of the configuration's own grammar rather than as a plain object
            Result<Atom, ProcessDefinitionConfiguration> cfg = processCfgVal.apply(in);
            if (!cfg.isSuccess()) {
                return cfg;
            }

            // the input is immutable, so the same position can be parsed again
            Result<Atom, Map<String, Serializable>> raw = mapVal.apply(in);
            if (raw.isSuccess()) {
                o.rawConfiguration(raw.toSuccess().getResult());
            }

            return cfg;
        };
    }

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
