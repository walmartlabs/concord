package com.walmartlabs.concord.plugins.lock;

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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Map;

import static com.walmartlabs.concord.plugins.lock.Constants.*;

public class TaskParams {

    private final Variables input;

    public TaskParams(Map<String, Object> input) {
        this(new MapBackedVariables(input));
    }

    public TaskParams(Variables input) {
        this.input = input;
    }

    public String lockName() {
        return input.assertString(LOCK_NAME_KEY);
    }

    public String scope() {
        return input.getString(SCOPE_KEY, PROJECT_SCOPE);
    }
}
