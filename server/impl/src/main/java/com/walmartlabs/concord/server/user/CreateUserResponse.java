package com.walmartlabs.concord.server.user;

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

public class CreateUserResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean ok = true;
    private final UUID id;
    private final String username;
    private final OperationResult result;

    @JsonCreator
    public CreateUserResponse(@JsonProperty("id") UUID id,
                              @JsonProperty("username") String username,
                              @JsonProperty("result") OperationResult result) {

        this.id = id;
        this.username = username;
        this.result = result;
    }

    public boolean isOk() {
        return ok;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public OperationResult getResult() {
        return result;
    }

    @Override
    public final String toString() {
        return "CreateUserResponse{" +
                "ok=" + ok +
                ", id=" + id +
                ", username='" + username + '\'' +
                ", result=" + result +
                '}';
    }
}
