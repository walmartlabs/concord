package com.walmartlabs.concord.server.security.identity;

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

import com.walmartlabs.concord.server.sdk.security.DirectoryQuery;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LocalUserDirectoryTest {
    @Test
    void validatesLocalNamespaceAndNormalizesQueries() {
        AtomicReference<Object[]> bindings = new AtomicReference<>();
        MockConnection connection = new MockConnection(ctx -> {
            bindings.set(ctx.bindings());
            return new MockResult[]{new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        });
        LocalUserDirectory directory = new LocalUserDirectory(DSL.using(connection, SQLDialect.POSTGRES).configuration());
        UUID id = UUID.randomUUID();
        assertTrue(directory.acceptsIdentity(new ExternalIdentityKey("local", "", id.toString())));
        assertFalse(directory.acceptsIdentity(new ExternalIdentityKey("local", "other", id.toString())));
        assertFalse(directory.acceptsIdentity(new ExternalIdentityKey("local", "", "not-a-uuid")));
        assertTrue(directory.find(new DirectoryQuery("username", "Alice", null)).isEmpty());
        assertArrayEquals(new Object[]{"alice", null}, bindings.get());
        assertThrows(IllegalArgumentException.class, () -> directory.find(new DirectoryQuery("email", "a", null)));
    }
}
