package com.walmartlabs.concord.runtime.v2.model;

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

import com.walmartlabs.concord.imports.Imports;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface ProcessDefinition extends Serializable {

    long serialVersionUID = 1L;

    @Value.Default
    default String runtime() {
        return "concord-v2"; // TODO constants
    }

    // resources

    @Value.Default
    default ProcessConfiguration configuration() {
        return ProcessConfiguration.builder().build();
    }

    @Value.Default
    default Map<String, List<Step>> flows() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Profile> profiles() {
        return Collections.emptyMap();
    }

    @Value.Default
    default List<Trigger> triggers() {
        return Collections.emptyList();
    }

    @Value.Default
    default Imports imports() {
        return Imports.builder().build();
    }

    @Value.Default
    default Forms forms() {
        return Forms.builder().build();
    }

    static ImmutableProcessDefinition.Builder builder() {
        return ImmutableProcessDefinition.builder();
    }

    static ProcessDefinition merge(ProcessDefinition a, ProcessDefinition b) {
        Map<String, Profile> profiles = new HashMap<>(a.profiles());
        for (Map.Entry<String, Profile> p : b.profiles().entrySet()) {
            Profile pa = profiles.getOrDefault(p.getKey(), Profile.builder().build());
            profiles.put(p.getKey(), Profile.merge(pa, p.getValue()));
        }

        return ProcessDefinition.builder().from(a)
                .configuration(ProcessConfiguration.merge(a.configuration(), b.configuration()))
                .putAllFlows(b.flows())
                .profiles(profiles)
                .addAllTriggers(b.triggers())
                .imports(Imports.merge(a.imports(), b.imports()))
                .forms(Forms.merge(a.forms(), b.forms()))
                .build();
    }
}
