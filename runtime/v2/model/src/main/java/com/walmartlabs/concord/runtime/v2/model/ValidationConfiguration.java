package com.walmartlabs.concord.runtime.v2.model;

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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serial;
import java.io.Serializable;

/**
 * Configuration for process validation settings.
 *
 * @param taskCalls validation settings for task calls
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValidationConfiguration(TaskCallValidation taskCalls) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationConfiguration {
        if (taskCalls == null) {
            taskCalls = new TaskCallValidation();
        }
    }

    public ValidationConfiguration() {
        this(new TaskCallValidation());
    }
}
