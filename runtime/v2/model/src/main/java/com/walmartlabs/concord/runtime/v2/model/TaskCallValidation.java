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
 * Configuration for task call validation.
 *
 * @param in  validation mode for task input parameters
 * @param out validation mode for task output parameters
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskCallValidation(
        ValidationMode in,
        ValidationMode out
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum ValidationMode {
        /**
         * No validation (default)
         */
        DISABLED,
        /**
         * Log warnings but continue
         */
        WARN,
        /**
         * Fail on validation errors
         */
        FAIL
    }

    public TaskCallValidation {
        if (in == null) in = ValidationMode.DISABLED;
        if (out == null) out = ValidationMode.DISABLED;
    }

    public TaskCallValidation() {
        this(ValidationMode.DISABLED, ValidationMode.DISABLED);
    }
}
