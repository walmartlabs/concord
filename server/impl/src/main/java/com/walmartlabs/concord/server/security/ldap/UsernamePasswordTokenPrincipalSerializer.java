package com.walmartlabs.concord.server.security.ldap;

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
import org.apache.shiro.authc.UsernamePasswordToken;

import javax.inject.Inject;

import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.bool;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.put;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.text;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toBytes;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toJsonNode;

public class UsernamePasswordTokenPrincipalSerializer extends AbstractPrincipalSerializer<UsernamePasswordToken> {

    private static final String USERNAME_PASSWORD_TYPE = "usernamePassword";

    @Inject
    public UsernamePasswordTokenPrincipalSerializer(ObjectMapper objectMapper) {
        super(objectMapper, UsernamePasswordToken.class, USERNAME_PASSWORD_TYPE);
    }

    @Override
    public byte[] serialize(UsernamePasswordToken principal) {
        var data = objectMapper.createObjectNode();
        put(data, "username", principal.getUsername());
        var password = principal.getPassword();
        put(data, "password", password != null ? new String(password) : null);
        data.put("rememberMe", principal.isRememberMe());
        return toBytes(objectMapper, data);
    }

    @Override
    public UsernamePasswordToken deserialize(byte[] data) {
        var node = toJsonNode(objectMapper, data);
        var username = text(node, "username");
        var password = text(node, "password");
        if (username == null && password == null) {
            return new UsernamePasswordToken();
        }
        return new UsernamePasswordToken(username, password != null ? password.toCharArray() : null, bool(node, "rememberMe"));
    }
}
