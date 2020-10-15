package com.walmartlabs.concord.server.org.secret;

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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.OperationResult;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecretOperationResponse implements Serializable {

    private final boolean ok = true;
    private final UUID id;
    private final OperationResult result;
    private final String password;

    @JsonCreator
    public SecretOperationResponse(@JsonProperty("id") UUID id,
                                   @JsonProperty("result") OperationResult result,
                                   @JsonProperty("password") String password) {
        this.id = id;
        this.result = result;
        this.password = password;
    }

    public boolean isOk() {
        return ok;
    }

    public UUID getId() {
        return id;
    }

    public OperationResult getResult() {
        return result;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public final String toString() {
        return "SecretOperationResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", result=" + result +
                '}';
    }
}
