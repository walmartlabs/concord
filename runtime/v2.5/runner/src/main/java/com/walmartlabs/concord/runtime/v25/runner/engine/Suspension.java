package com.walmartlabs.concord.runtime.v25.runner.engine;

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

import com.walmartlabs.concord.runtime.v25.model.Values;

import java.io.Serializable;
import java.util.Map;

public record Suspension(String eventName, boolean reentrant, String taskName, Map<String, Object> payload,
                         int instructionId, String path) implements Serializable {

    public Suspension {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("Suspension event name must not be empty");
        }
        payload = Values.map(payload);
    }
}
