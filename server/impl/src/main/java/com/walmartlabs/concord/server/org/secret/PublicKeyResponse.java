package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.OperationResult;

import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class PublicKeyResponse extends SecretOperationResponse {

    private static final long serialVersionUID = 1L;

    private final String publicKey;

    @JsonCreator
    public PublicKeyResponse(@JsonProperty("id") UUID id,
                             @JsonProperty("result") OperationResult result,
                             @JsonProperty("password") String password,
                             @JsonProperty("publicKey") String publicKey) {

        super(id, result, password);
        this.publicKey = publicKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public String toString() {
        return "PublicKeyResponse{" +
                "publicKey='" + publicKey + '\'' +
                "} " + super.toString();
    }
}
