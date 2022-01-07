package com.walmartlabs.concord.server.org.inventory;

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
import java.util.UUID;

@Deprecated
public class CreateInventoryQueryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean ok = true;

    private final OperationResult result;
    private final UUID id;

    @JsonCreator
    public CreateInventoryQueryResponse(@JsonProperty("result") OperationResult result,
                                        @JsonProperty("id") UUID id) {

        this.result = result;
        this.id = id;
    }

    public boolean isOk() {
        return ok;
    }

    public OperationResult getResult() {
        return result;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String toString() {
        return "CreateInventoryQueryResponse{" +
                "ok=" + ok +
                ", result=" + result +
                ", id=" + id +
                '}';
    }
}
