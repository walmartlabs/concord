package com.walmartlabs.concord.runtime.v25.runner.plan;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v25.model.Configuration25;
import com.walmartlabs.concord.runtime.model.Form;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ExecutionPlan(String id, Configuration25 configuration, Map<String, FlowPlan> flows,
                            Set<String> publicFlows, Map<String, Form> forms) implements Serializable {

    public ExecutionPlan {
        flows = Collections.unmodifiableMap(new LinkedHashMap<>(flows));
        publicFlows = Set.copyOf(publicFlows);
        forms = Collections.unmodifiableMap(new LinkedHashMap<>(forms));
    }

    public FlowPlan flow(String name) {
        var result = flows.get(name);
        if (result == null) {
            throw new IllegalArgumentException("Unknown flow: " + name);
        }
        return result;
    }

    public record FlowPlan(int id, String name, List<Instruction> instructions) implements Serializable {

        public FlowPlan {
            instructions = List.copyOf(instructions);
        }
    }
}
