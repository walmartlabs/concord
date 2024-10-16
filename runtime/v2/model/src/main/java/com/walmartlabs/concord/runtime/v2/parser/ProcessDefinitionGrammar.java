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

import static com.walmartlabs.concord.runtime.v2.parser.ConfigurationGrammar.processCfgVal;
import static com.walmartlabs.concord.runtime.v2.parser.FlowsGrammar.flowsVal;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.betweenTokens;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarMisc.with;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.optional;
import static com.walmartlabs.concord.runtime.v2.parser.GrammarOptions.options;
import static com.walmartlabs.concord.runtime.v2.parser.ProfilesGrammar.profilesVal;
import static com.walmartlabs.concord.runtime.v2.parser.PublicFlowsGrammar.publicFlowsVal;
import static com.walmartlabs.concord.runtime.v2.parser.TriggersGrammar.triggersVal;

public final class ProcessDefinitionGrammar {

    public static final Parser<Atom, ProcessDefinition> processDefinition =
            betweenTokens(JsonToken.START_OBJECT, JsonToken.END_OBJECT,
                    with(ImmutableProcessDefinition::builder,
                            o -> options(
                                    optional("configuration", processCfgVal.map(o::configuration)),
                                    optional("flows", flowsVal.map(o::flows)),
                                    optional("publicFlows", publicFlowsVal.map(o::publicFlows)),
                                    optional("profiles", profilesVal.map(o::profiles)),
                                    optional("triggers", triggersVal.map(o::triggers)),
                                    optional("forms", FormsGrammar.formsVal.map(o::forms)),
                                    optional("imports", ImportsGrammar.importsVal.map(o::imports)),
                                    optional("resources", ResourcesGrammar.resourcesVal.map(o::resources))))
                            .map(ImmutableProcessDefinition.Builder::build));

    private ProcessDefinitionGrammar() {
    }
}
