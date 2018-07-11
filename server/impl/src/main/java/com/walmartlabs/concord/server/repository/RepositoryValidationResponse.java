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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.OperationResult;

import java.io.Serializable;

public class RepositoryValidationResponse implements Serializable {
    private final boolean ok = true;
    private final OperationResult result;

    @JsonCreator
    public RepositoryValidationResponse(@JsonProperty("result") OperationResult result) {
        this.result = result;
    }

    public OperationResult getResult() {
        return result;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "CreateTeamResponse{" +
                "ok=" + ok +
                ", result=" + result +
                '}';
    }

}
