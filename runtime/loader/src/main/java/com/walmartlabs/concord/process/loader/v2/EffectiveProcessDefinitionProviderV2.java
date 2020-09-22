package com.walmartlabs.concord.process.loader.v2;

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

import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.process.loader.model.EffectiveProcessDefinitionProvider;
import com.walmartlabs.concord.process.loader.model.Options;
import com.walmartlabs.concord.runtime.v2.ProjectSerializerV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Profile;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.sdk.MapUtils;

import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EffectiveProcessDefinitionProviderV2 implements EffectiveProcessDefinitionProvider {

    private final ProjectSerializerV2 serializer = new ProjectSerializerV2();
    private final ProcessDefinition delegate;

    public EffectiveProcessDefinitionProviderV2(ProcessDefinition delegate) {
        this.delegate = delegate;
    }

    @Override
    public void serialize(Options options, OutputStream out) throws Exception {
        Map<String, Object> arguments = MapUtils.getMap(options.configuration(), "arguments", Collections.emptyMap());

        Map<String, List<Step>> flows = new HashMap<>(delegate.flows());
        for (String ap : options.activeProfiles()) {
            Profile p = delegate.profiles().get(ap);
            if (p != null) {
                flows.putAll(p.flows());
            }
        }
        ProcessDefinition pd = ProcessDefinition.builder().from(delegate)
                .configuration(ProcessConfiguration.builder().from(delegate.configuration())
                        .arguments(arguments)
                        .build())
                .flows(flows)
                .imports(Imports.builder().build())
                .profiles(Collections.emptyMap())
                .build();

        serializer.write(pd, out);
    }
}
