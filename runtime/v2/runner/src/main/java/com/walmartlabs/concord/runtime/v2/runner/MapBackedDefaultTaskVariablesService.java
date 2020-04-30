package com.walmartlabs.concord.runtime.v2.runner;

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

import java.util.Collections;
import java.util.Map;

public class MapBackedDefaultTaskVariablesService implements DefaultTaskVariablesService {

    private final Map<String, Map<String, Object>> variables;

    public MapBackedDefaultTaskVariablesService(Map<String, Map<String, Object>> variables) {
        this.variables = Collections.unmodifiableMap(variables);
    }

    @Override
    public Map<String, Object> get(String taskName) {
        return variables.getOrDefault(taskName, Collections.emptyMap());
    }
}
