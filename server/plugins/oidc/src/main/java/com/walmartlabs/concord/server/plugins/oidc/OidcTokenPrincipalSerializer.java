package com.walmartlabs.concord.server.plugins.oidc;

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

import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toBytes;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toJsonNode;

public class OidcTokenPrincipalSerializer extends AbstractPrincipalSerializer<OidcToken> {

    private static final String OIDC_TOKEN_TYPE = "oidcToken";

    @Inject
    public OidcTokenPrincipalSerializer(ObjectMapper objectMapper) {
        super(objectMapper, OidcToken.class, OIDC_TOKEN_TYPE);
    }

    @Override
    public byte[] serialize(OidcToken principal) {
        var data = objectMapper.createObjectNode();
        data.set("profile", objectMapper.valueToTree(principal.getProfile()));
        return toBytes(objectMapper, data);
    }

    @Override
    public OidcToken deserialize(byte[] data) throws Exception {
        var node = toJsonNode(objectMapper, data);
        var profile = objectMapper.treeToValue(node.get("profile"), UserProfile.class);
        return new OidcToken(profile);
    }
}
