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

import com.walmartlabs.concord.runtime.v25.model.SourceRange;
import com.walmartlabs.concord.runtime.v25.model.Values;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Instruction(int id, Opcode opcode, String sourceType, Object value, Map<String, Object> options,
                          Map<String, List<Instruction>> branches, SourceRange sourceRange,
                          String path) implements Serializable {

    public Instruction {
        value = Values.freeze(value);
        options = Values.map(options);
        var copied = new LinkedHashMap<String, List<Instruction>>();
        branches.forEach((name, instructions) -> copied.put(name, List.copyOf(instructions)));
        branches = Collections.unmodifiableMap(copied);
    }

    public List<Instruction> branch(String name) {
        return branches.getOrDefault(name, List.of());
    }
}
