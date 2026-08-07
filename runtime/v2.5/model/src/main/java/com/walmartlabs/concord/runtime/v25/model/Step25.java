package com.walmartlabs.concord.runtime.v25.model;

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

import com.walmartlabs.concord.runtime.model.SourceMap;
import com.walmartlabs.concord.runtime.model.Step;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Step25(String type, Object value, Map<String, Object> options,
                     Map<String, List<Step25>> branches, SourceRange sourceRange, String path,
                     SourceRange valueRange, Map<String, SourceRange> optionRanges) implements Step {

    public Step25(String type, Object value, Map<String, Object> options, Map<String, List<Step25>> branches,
                  SourceRange sourceRange, String path) {
        this(type, value, options, branches, sourceRange, path, sourceRange, Map.of());
    }

    public Step25 {
        value = Values.freeze(value);
        options = Values.map(options);
        var copiedBranches = new LinkedHashMap<String, List<Step25>>(branches.size());
        branches.forEach((name, steps) -> copiedBranches.put(name, Values.list(steps)));
        branches = Collections.unmodifiableMap(copiedBranches);
        optionRanges = Collections.unmodifiableMap(new LinkedHashMap<>(optionRanges));
    }

    public List<Step25> branch(String name) {
        return branches.getOrDefault(name, List.of());
    }

    @Override
    public SourceMap location() {
        return sourceRange.toSourceMap();
    }
}
