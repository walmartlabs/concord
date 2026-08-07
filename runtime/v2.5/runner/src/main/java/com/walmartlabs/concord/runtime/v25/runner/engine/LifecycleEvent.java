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

import java.util.Map;

public record LifecycleEvent(Type type, String correlationId, String eventName, int instructionId,
                             String source, int line, int column, String path, Map<String, Object> data) {

    public LifecycleEvent {
        data = Values.map(data);
    }

    public enum Type {
        STEP_STARTED,
        STEP_COMPLETED,
        CHECKPOINT_SAVED,
        SUSPENDED,
        RESUMED
    }
}
