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
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import java.io.OutputStream;
import java.util.*;

public class EffectiveProcessDefinitionProviderV2 implements EffectiveProcessDefinitionProvider {

    private final ProjectSerializerV2 serializer = new ProjectSerializerV2();
    private final ProcessDefinition delegate;

    public EffectiveProcessDefinitionProviderV2(ProcessDefinition delegate) {
        this.delegate = delegate;
    }

    @Override
    public void serialize(Options options, OutputStream out) throws Exception {
        Map<String, List<Step>> flows = new HashMap<>(delegate.flows());
        for (String ap : options.activeProfiles()) {
            Profile p = delegate.profiles().get(ap);
            if (p != null) {
                flows.putAll(p.flows());
            }
        }

        Map<String, Object> arguments = new LinkedHashMap<>(MapUtils.getMap(options.configuration(), "arguments", Collections.emptyMap()));
        arguments.put(Constants.Context.TX_ID_KEY, options.instanceId());
        if (options.parentInstanceId() != null) {
            arguments.put(Constants.Request.PARENT_INSTANCE_ID_KEY, options.parentInstanceId());
        }
        arguments.put(Constants.Request.INITIATOR_KEY, options.configuration().get(Constants.Request.INITIATOR_KEY));
        arguments.put(Constants.Request.PROJECT_INFO_KEY, MapUtils.getMap(options.configuration(), Constants.Request.PROJECT_INFO_KEY, Collections.emptyMap()));
        arguments.put(Constants.Request.PROCESS_INFO_KEY, MapUtils.getMap(options.configuration(), Constants.Request.PROCESS_INFO_KEY, Collections.emptyMap()));

        ProcessDefinition pd = ProcessDefinition.builder().from(delegate)
                .configuration(ProcessDefinitionConfiguration.builder().from(delegate.configuration())
                        .entryPoint(options.entryPoint())
                        .arguments(arguments)
                        .build())
                .flows(flows)
                .imports(Imports.builder().build())
                .profiles(Collections.emptyMap())
                .build();

        serializer.write(pd, out);
    }
}
