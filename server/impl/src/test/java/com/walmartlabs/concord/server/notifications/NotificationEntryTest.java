package com.walmartlabs.concord.server.notifications;

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

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationEntryTest {

    // ---------------------------------------------------------------------------
    // effectiveOwnerKind
    // ---------------------------------------------------------------------------

    @Test
    public void effectiveOwnerKind_userOwner() {
        UUID userId = UUID.randomUUID();
        NotificationEntry entry = entryWithOwners(userId, null, null);

        assertEquals(Optional.of(NotificationOwnerKind.USER), entry.effectiveOwnerKind());
    }

    @Test
    public void effectiveOwnerKind_projectOwner() {
        UUID projectId = UUID.randomUUID();
        NotificationEntry entry = entryWithOwners(null, projectId, null);

        assertEquals(Optional.of(NotificationOwnerKind.PROJECT), entry.effectiveOwnerKind());
    }

    @Test
    public void effectiveOwnerKind_orgOwner() {
        UUID orgId = UUID.randomUUID();
        NotificationEntry entry = entryWithOwners(null, null, orgId);

        assertEquals(Optional.of(NotificationOwnerKind.ORG), entry.effectiveOwnerKind());
    }

    @Test
    public void effectiveOwnerKind_noOwner_returnsEmpty() {
        NotificationEntry entry = entryWithOwners(null, null, null);

        assertEquals(Optional.empty(), entry.effectiveOwnerKind());
    }

    // ---------------------------------------------------------------------------
    // effectiveOwnerId
    // ---------------------------------------------------------------------------

    @Test
    public void effectiveOwnerId_userOwner() {
        UUID userId = UUID.randomUUID();
        NotificationEntry entry = entryWithOwners(userId, null, null);

        assertEquals(Optional.of(userId), entry.effectiveOwnerId());
    }

    @Test
    public void effectiveOwnerId_projectOwner() {
        UUID projectId = UUID.randomUUID();
        NotificationEntry entry = entryWithOwners(null, projectId, null);

        assertEquals(Optional.of(projectId), entry.effectiveOwnerId());
    }

    @Test
    public void effectiveOwnerId_orgOwner() {
        UUID orgId = UUID.randomUUID();
        NotificationEntry entry = entryWithOwners(null, null, orgId);

        assertEquals(Optional.of(orgId), entry.effectiveOwnerId());
    }

    @Test
    public void effectiveOwnerId_noOwner_returnsEmpty() {
        NotificationEntry entry = entryWithOwners(null, null, null);

        assertEquals(Optional.empty(), entry.effectiveOwnerId());
    }

    // ---------------------------------------------------------------------------
    // Consistency: kind and id agree on the same owner
    // ---------------------------------------------------------------------------

    @Test
    public void effectiveKindAndId_alwaysAgree() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        assertOwnerConsistency(entryWithOwners(userId, null, null));
        assertOwnerConsistency(entryWithOwners(null, projectId, null));
        assertOwnerConsistency(entryWithOwners(null, null, orgId));
        assertOwnerConsistency(entryWithOwners(null, null, null));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static NotificationEntry entryWithOwners(UUID userId, UUID projectId, UUID orgId) {
        return new NotificationEntry(
                null,
                userId,
                orgId,
                projectId,
                null,
                "summary",
                null,
                null,
                false,
                OffsetDateTime.now(),
                null,
                null
        );
    }

    private static void assertOwnerConsistency(NotificationEntry entry) {
        Optional<NotificationOwnerKind> kind = entry.effectiveOwnerKind();
        Optional<UUID> id = entry.effectiveOwnerId();

        assertEquals(kind.isPresent(), id.isPresent(),
                "effectiveOwnerKind and effectiveOwnerId must both be present or both be empty");
    }
}
