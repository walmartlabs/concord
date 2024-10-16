package com.walmartlabs.concord.process.loader.v2;

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

import com.walmartlabs.concord.process.loader.model.Configuration;
import com.walmartlabs.concord.process.loader.model.FlowDefinition;
import com.walmartlabs.concord.process.loader.model.Profile;

import java.io.Serializable;
import java.util.*;

public class ProfileV2 implements Profile, Serializable {

    private static final long serialVersionUID = 1L;

    private final Configuration cfg;
    private final Set<String> publicFlows;
    private final Map<String, FlowDefinition> flows;

    public ProfileV2(com.walmartlabs.concord.runtime.v2.model.Profile delegate) {
        this.cfg = new ConfigurationV2(delegate.configuration());

        this.publicFlows = delegate.publicFlows() != null ? new HashSet<>(delegate.publicFlows()) : Collections.emptySet();

        this.flows = new HashMap<>();
        if (delegate.flows() != null) {
            delegate.flows().forEach((k, v) -> flows.put(k, new FlowDefinitionV2(k, v.steps())));
        }
    }

    @Override
    public Configuration configuration() {
        return cfg;
    }

    @Override
    public Set<String> publicFlows() {
        return publicFlows;
    }

    @Override
    public Map<String, FlowDefinition> flows() {
        return flows;
    }
}
