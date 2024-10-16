package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backward compatibility for reading old {@link ProcessSnapshot} instances.
 */
public final class StateBackwardCompatibility {

    public static ProcessSnapshot apply(ProcessSnapshot processSnapshot) {
        if (processSnapshot == null) {
            return null;
        }

        // ProcessDefinition.flows and ProcessDefinition.profiles.flows has changed
        // from Map<String, List<Step>> to Map<String, Flow>.
        // Here we convert the old format to the new one.

        ProcessDefinition processDefinition = processSnapshot.processDefinition();
        if (isOldFlowsDefinition(processDefinition.flows())) {
            return ProcessSnapshot.builder().from(processSnapshot)
                    .processDefinition(ProcessDefinition.builder()
                            .configuration(processDefinition.configuration())
                            .flows(fixFlows(processDefinition.flows()))
                            .publicFlows(processDefinition.publicFlows())
                            .profiles(fixProfiles(processDefinition.profiles()))
                            .triggers(processDefinition.triggers())
                            .imports(processDefinition.imports())
                            .forms(processDefinition.forms())
                            .resources(processDefinition.resources())
                            .build())
                    .build();
        }
        return processSnapshot;
    }

    private static Map<String, Profile> fixProfiles(Map<String, Profile> profiles) {
        Map<String, Profile> result = new LinkedHashMap<>(profiles.size());
        for (var e : profiles.entrySet()) {
            Profile p = e.getValue();
            result.put(e.getKey(), Profile.builder()
                    .configuration(p.configuration())
                    .publicFlows(p.publicFlows())
                    .flows(fixFlows(p.flows()))
                    .forms(p.forms())
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Flow> fixFlows(Map<String, Flow> flows) {
        Map<String, Flow> result = new LinkedHashMap<>(flows.size());
        for (var e : flows.entrySet()) {
            result.put(e.getKey(), Flow.of(Location.builder().build(), (List<Step>) e.getValue()));
        }
        return result;
    }

    private static boolean isOldFlowsDefinition(Map<String, Flow> flows) {
        Object flowOrStepsArray = flows.values().stream().findFirst().orElse(null);
        return (flowOrStepsArray instanceof List<?>);
    }

    private StateBackwardCompatibility() {
    }
}
