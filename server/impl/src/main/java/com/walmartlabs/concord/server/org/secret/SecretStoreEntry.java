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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class SecretStoreEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    private final String storeType;
    @NotNull
    private final String description;

    @JsonCreator
    public SecretStoreEntry(@JsonProperty("storeType") String storeType,
                            @JsonProperty("description") String description) {

        this.storeType = storeType;
        this.description = description;
    }

    public String getStoreType() {
        return storeType;
    }

    public String getDescription() {
        return description;
    }
}
