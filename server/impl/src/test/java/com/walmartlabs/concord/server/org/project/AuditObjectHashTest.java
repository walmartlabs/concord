package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.user.RoleEntry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuditObjectHashTest {
    @Test
    void hashesLeafSnapshots() {
        UUID id = UUID.fromString("12345678-1234-1234-9234-123456789abc");
        assertEquals("4224af9a2f7d9827fe99bdc371a63fb5", AuditObjectHash.hash(new RoleEntry(id, "role", Set.of("read"))));
        assertEquals("4818fe2607a42dba94f7af81105b818d", AuditObjectHash.hash(new RoleEntry(id, "role", Set.of("write"))));
        assertEquals("7ac43c7fd60fec291b9cbb3b148c692a", AuditObjectHash.hash(new OrganizationEntry("org-one", Map.of("scalar", "before"))));
        assertEquals("0627aaf4031679ed6738252572323c58", AuditObjectHash.hash(new OrganizationEntry("org-two", Map.of("scalar", "after"))));
    }

    @Test
    void hashesDirectRepositorySnapshots() {
        ProjectEntry oldProject = new ProjectEntry("project", Map.of("repo", new RepositoryEntry("repo", "https://example.com/old.git")));
        ProjectEntry newProject = new ProjectEntry("project", Map.of("repo", new RepositoryEntry("repo", "https://example.com/new.git")));
        assertEquals("373a1e1edba39e21ba8e9dd3c7370369", AuditObjectHash.hash(oldProject));
        assertEquals("18f97a56fb88e9314a4716e284a7a776", AuditObjectHash.hash(newProject));
    }

    @Test
    void hashesProjectWithOffsetDateTimeAndOwner() {
        UUID id = UUID.fromString("12345678-1234-1234-9234-123456789abc");
        ProjectEntry project = new ProjectEntry(id, "project", null, id, "org",
                Map.of("repo", new RepositoryEntry("repo", "https://example.com/a.git")),
                null, null, EntityOwner.builder().username("alice").build(), null, null, null, null,
                OffsetDateTime.parse("2026-01-01T01:02:00+03:00"));
        assertEquals("e1b6078fafad88a809edaf67387283be", AuditObjectHash.hash(project));
    }

    @Test
    void hashesDirectOwnerSnapshot() {
        EntityOwner alice = EntityOwner.builder().username("alice").build();
        EntityOwner bob = EntityOwner.builder().username("bob").build();
        OrganizationEntry first = new OrganizationEntry(null, "org", alice, null, null, null);
        OrganizationEntry same = new OrganizationEntry(null, "org", EntityOwner.builder().username("alice").build(), null, null, null);
        OrganizationEntry changed = new OrganizationEntry(null, "org", bob, null, null, null);
        assertEquals(AuditObjectHash.hash(first), AuditObjectHash.hash(same));
        assertNotEquals(AuditObjectHash.hash(first), AuditObjectHash.hash(changed));
    }
}
