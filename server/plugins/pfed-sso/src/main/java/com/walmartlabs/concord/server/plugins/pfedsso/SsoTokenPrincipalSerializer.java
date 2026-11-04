package com.walmartlabs.concord.server.plugins.pfedsso;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.security.AbstractPrincipalSerializer;

import javax.inject.Inject;
import java.util.Set;

import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.put;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.text;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toBytes;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toJsonNode;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.value;

public class SsoTokenPrincipalSerializer extends AbstractPrincipalSerializer<SsoToken> {

    private static final String SSO_TOKEN_TYPE = "ssoToken";
    private static final TypeReference<Set<String>> SET_OF_STRINGS = new TypeReference<Set<String>>() {
    };

    @Inject
    public SsoTokenPrincipalSerializer(ObjectMapper objectMapper) {
        super(objectMapper, SsoToken.class, SSO_TOKEN_TYPE);
    }

    @Override
    public byte[] serialize(SsoToken principal) {
        var data = objectMapper.createObjectNode();
        put(data, "username", principal.getUsername());
        put(data, "domain", principal.getDomain());
        put(data, "displayName", principal.getDisplayName());
        put(data, "mail", principal.getMail());
        put(data, "userPrincipalName", principal.getUserPrincipalName());
        put(data, "nameInNamespace", principal.getNameInNamespace());
        data.set("groups", objectMapper.valueToTree(principal.getGroups()));
        return toBytes(objectMapper, data);
    }

    @Override
    public SsoToken deserialize(byte[] data) {
        var node = toJsonNode(objectMapper, data);
        return new SsoToken(
                text(node, "username"),
                text(node, "domain"),
                text(node, "displayName"),
                text(node, "mail"),
                text(node, "userPrincipalName"),
                text(node, "nameInNamespace"),
                value(objectMapper, node, "groups", SET_OF_STRINGS));
    }
}
