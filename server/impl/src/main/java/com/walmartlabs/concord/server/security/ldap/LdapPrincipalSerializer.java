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

import javax.inject.Inject;

import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.LDAP_PRINCIPAL_TYPE;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.ldapPrincipal;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toBytes;
import static com.walmartlabs.concord.server.security.PrincipalSerializerSupport.toJsonNode;

public class LdapPrincipalSerializer extends AbstractPrincipalSerializer<LdapPrincipal> {

    @Inject
    public LdapPrincipalSerializer(ObjectMapper objectMapper) {
        super(objectMapper, LdapPrincipal.class, LDAP_PRINCIPAL_TYPE);
    }

    @Override
    public byte[] serialize(LdapPrincipal principal) {
        return toBytes(objectMapper, toJsonNode(objectMapper, principal));
    }

    @Override
    public LdapPrincipal deserialize(byte[] data) {
        return ldapPrincipal(objectMapper, toJsonNode(objectMapper, data));
    }
}
