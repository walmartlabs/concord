package com.walmartlabs.concord.server.security.github;

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

import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.put;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.text;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toBytes;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toJsonNode;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.uuid;

public class GithubKeyPrincipalSerializer extends AbstractPrincipalSerializer<GithubKey> {

    private static final String GITHUB_KEY_TYPE = "githubKey";

    @Inject
    public GithubKeyPrincipalSerializer(ObjectMapper objectMapper) {
        super(objectMapper, GithubKey.class, GITHUB_KEY_TYPE);
    }

    @Override
    public byte[] serialize(GithubKey principal) {
        var data = objectMapper.createObjectNode();
        put(data, "key", principal.getKey());
        put(data, "projectId", principal.getProjectId());
        put(data, "repoToken", principal.getRepoToken());
        return toBytes(objectMapper, data);
    }

    @Override
    public GithubKey deserialize(byte[] data) {
        var node = toJsonNode(objectMapper, data);
        return new GithubKey(text(node, "key"), uuid(node, "projectId"), text(node, "repoToken"));
    }
}
