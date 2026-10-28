package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionIdentityCodecTest {
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    void roundTripJson() {
        ExecutionIdentity identity = new ExecutionIdentity(ID, "custom\"provider");
        byte[] data = ExecutionIdentityCodec.encode(identity);
        assertEquals("{\"version\":1,\"userId\":\"12345678-1234-1234-1234-123456789abc\",\"authenticationProviderId\":\"custom\\\"provider\"}", new String(data, StandardCharsets.UTF_8));
        assertFalse(ExecutionIdentityCodec.isLegacy(data));
        assertEquals(identity, ExecutionIdentityCodec.decode(data));
    }

    @Test
    void readsOldAndTransitionalPrincipals() throws Exception {
        UserPrincipal old = oldPrincipal(ID, "ldapRealm");
        SimplePrincipalCollection collection = new SimplePrincipalCollection();
        collection.add(old, "ldapRealm");
        collection.add("unrelated", "other");
        byte[] bytes = serialize(collection);
        assertTrue(ExecutionIdentityCodec.isLegacy(bytes));
        assertEquals(new ExecutionIdentity(ID, "ldapRealm"), ExecutionIdentityCodec.decode(bytes));
        SubjectPrincipal current = new SubjectPrincipal(ID, "user", null, new AuthenticationContext("custom", null, null));
        assertEquals(new ExecutionIdentity(ID, "custom"), ExecutionIdentityCodec.decode(serialize(List.of("other", current))));
        assertEquals(new ExecutionIdentity(ID, "ldapRealm"), ExecutionIdentityCodec.decode(serialize(List.of(old, oldPrincipal(ID, "ldapRealm")))));
    }

    @Test
    void rejectsConflictsAndMissingIdentity() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(serialize(List.of(oldPrincipal(ID, "a"), oldPrincipal(UUID.randomUUID(), "a")))));
        assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(serialize(List.of(oldPrincipal(ID, "a"), oldPrincipal(ID, "b")))));
        assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(serialize(List.of("not a user"))));
        assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(serialize("not a collection")));
    }

    @Test
    void rejectsInvalidJsonAndCorruption() throws Exception {
        String valid = new String(ExecutionIdentityCodec.encode(new ExecutionIdentity(ID, "local")), StandardCharsets.UTF_8);
        for (String invalid : List.of(valid.replace("\"version\":1", "\"version\":2"),
                valid.replace("\"version\":1", "\"version\":1,\"version\":1"),
                valid.replace("\"version\":1", "\"version\":\"1\""),
                valid.replace("local", ""), valid.replace(ID.toString(), "1-1-1-1-1"),
                valid.replace(",\"authenticationProviderId\":\"local\"", ""),
                valid.replace("}", ",\"extra\":1}"), valid + " {}", valid.substring(0, valid.length() - 1))) {
            assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(invalid.getBytes(StandardCharsets.UTF_8)), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(new byte[]{(byte) 0xc3, 0x28}));
        byte[] legacy = serialize(List.of(oldPrincipal(ID, "ldap")));
        assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(Arrays.copyOf(legacy, legacy.length - 1)));
    }

    @Test
    void reportsMissingPluginClass() throws Exception {
        byte[] bytes = serialize(List.of(new MissingPluginPrincipal()));
        byte[] name = MissingPluginPrincipal.class.getName().getBytes(StandardCharsets.UTF_8);
        int start = -1;
        for (int i = 0; i <= bytes.length - name.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(bytes, i, i + name.length), name)) {
                start = i;
                break;
            }
        }
        assertTrue(start >= 0);
        bytes[start + name.length - 1] = (byte) 'X';
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ExecutionIdentityCodec.decode(bytes));
        assertTrue(error.getMessage().contains("Missing class"));
        assertInstanceOf(ClassNotFoundException.class, error.getCause());
    }

    private static UserPrincipal oldPrincipal(UUID id, String realm) {
        return new UserPrincipal(realm, new UserEntry(id, "user", null, null, null, null, null, null, false, null, false));
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static class MissingPluginPrincipal implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
