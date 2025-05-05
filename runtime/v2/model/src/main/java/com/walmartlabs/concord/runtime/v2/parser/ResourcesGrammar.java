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
import com.walmartlabs.concord.runtime.v2.model.ImmutableConcordCliResources;
import com.walmartlabs.concord.runtime.v2.model.ImmutableResources;
import com.walmartlabs.concord.runtime.v2.model.Resources;
import com.walmartlabs.concord.runtime.v2.model.Resources.ConcordCliResources;
import io.takari.parc.Parser;

import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.*;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarV2.stringArrayVal;

public final class ResourcesGrammar {

    private static final Parser<Atom, ConcordCliResources> concordCliResources =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableConcordCliResources::builder,
                            o -> options(
                                    optional("includes", stringArrayVal.map(o::includes)),
                                    optional("excludes", stringArrayVal.map(o::excludes))))
                            .map(ImmutableConcordCliResources.Builder::build));

    private static final Parser<Atom, Resources> resources =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableResources::builder,
                            o -> options(
                                    optional("concord", stringArrayVal.map(o::concord)),
                                    optional("concordCli", concordCliResources.map(o::concordCli))))
                            .map(ImmutableResources.Builder::build));

    public static final Parser<Atom, Resources> resourcesVal =
            orError(resources, YamlValueType.RESOURCES);

    private ResourcesGrammar() {
    }
}
