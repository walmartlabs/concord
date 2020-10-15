package com.walmartlabs.concord.server.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.OperationResult;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RepositoryValidationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean ok;
    private final OperationResult result;

    private final List<String> errors;
    private final List<String> warnings;

    @JsonCreator
    public RepositoryValidationResponse(@JsonProperty("ok") boolean ok,
                                        @JsonProperty("result") OperationResult result,
                                        @JsonProperty("errors") List<String> errors,
                                        @JsonProperty("warnings") List<String> warnings) {
        this.ok = ok;
        this.result = result;
        this.errors = errors;
        this.warnings = warnings;
    }

    public OperationResult getResult() {
        return result;
    }

    public boolean isOk() {
        return ok;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public final String toString() {
        return "RepositoryValidationResponse{" +
                "ok=" + ok +
                ", result=" + result +
                ", errors=" + errors +
                ", warnings=" + warnings +
                '}';
    }
}
