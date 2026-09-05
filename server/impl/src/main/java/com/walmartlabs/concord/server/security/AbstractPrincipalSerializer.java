package com.walmartlabs.concord.server.security;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.sdk.security.PrincipalSerializer;

import java.util.Objects;

public abstract class AbstractPrincipalSerializer<T> implements PrincipalSerializer<T> {

    protected final ObjectMapper objectMapper;

    private final Class<T> principalType;
    private final String type;

    protected AbstractPrincipalSerializer(ObjectMapper objectMapper, Class<T> principalType, String type) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.principalType = Objects.requireNonNull(principalType);
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public Class<T> principalType() {
        return principalType;
    }

    @Override
    public String type() {
        return type;
    }
}
