package com.walmartlabs.concord.server.security.apikey;

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
import com.walmartlabs.concord.server.security.AbstractPrincipalSerializer;

import javax.inject.Inject;

import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.bool;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.put;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.text;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toBytes;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toJsonNode;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.uuid;

public class ApiKeyPrincipalSerializer extends AbstractPrincipalSerializer<ApiKey> {

    private static final String API_KEY_TYPE = "apiKey";

    @Inject
    public ApiKeyPrincipalSerializer(ObjectMapper objectMapper) {
        super(objectMapper, ApiKey.class, API_KEY_TYPE);
    }

    @Override
    public byte[] serialize(ApiKey principal) {
        var data = objectMapper.createObjectNode();
        put(data, "keyId", principal.getKeyId());
        put(data, "userId", principal.getUserId());
        put(data, "key", principal.getKey());
        data.put("rememberMe", principal.isRememberMe());
        return toBytes(objectMapper, data);
    }

    @Override
    public ApiKey deserialize(byte[] data) {
        var node = toJsonNode(objectMapper, data);
        return new ApiKey(
                uuid(node, "keyId"),
                uuid(node, "userId"),
                text(node, "key"),
                bool(node, "rememberMe"));
    }
}
