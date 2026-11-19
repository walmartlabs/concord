package com.walmartlabs.concord.server.sdk.identity;

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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ExternalIdentityTest {

    @Test
    public void testIdentityNamespace() {
        var identity = identity();

        assertEquals(identity, new ExternalIdentityRef("provider", "authority", "subject"));
        assertNotEquals(identity, new ExternalIdentityRef("other", "authority", "subject"));
        assertNotEquals(identity, new ExternalIdentityRef("provider", "other", "subject"));
        assertNotEquals(identity, new ExternalIdentityRef("provider", "authority", "other"));
        assertNotEquals(identity, new ExternalIdentityRef("provider", "authority", "SUBJECT"));
        assertEquals(" subject ", new ExternalIdentityRef("provider", "authority", " subject ").subjectId());
    }

    @Test
    public void testRequiredIdentityComponents() {
        assertThrows(NullPointerException.class, () -> new ExternalIdentityRef(null, "authority", "subject"));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityRef("provider", null, "subject"));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityRef("provider", "authority", null));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentityRef("", "authority", "subject"));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentityRef("provider", " ", "subject"));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentityRef("provider", "authority", "\t"));
    }

    @Test
    public void testSnapshotCopiesMemberships() {
        var groups = new HashSet<>(Set.of("CN=team,DC=example"));
        var snapshot = new ExternalIdentitySnapshot(identity(), ExternalIdentitySnapshot.Status.ACTIVE,
                "Example App", null, groups);
        groups.clear();

        assertEquals(Set.of("CN=team,DC=example"), snapshot.groups());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.groups().clear());
    }

    @Test
    public void testLifecycleStates() {
        for (var status : ExternalIdentitySnapshot.Status.values()) {
            var snapshot = new ExternalIdentitySnapshot(identity(), status, null, null, Set.of());
            assertEquals(status, snapshot.status());
            assertTrue(snapshot.groups().isEmpty());
        }

        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentitySnapshot(identity(),
                ExternalIdentitySnapshot.Status.INACTIVE, null, null, Set.of("group")));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentitySnapshot(identity(),
                ExternalIdentitySnapshot.Status.NOT_FOUND, null, null, Set.of("group")));
    }

    @Test
    public void testInvalidSnapshots() {
        assertThrows(NullPointerException.class, () -> new ExternalIdentitySnapshot(null,
                ExternalIdentitySnapshot.Status.ACTIVE, null, null, Set.of()));
        assertThrows(NullPointerException.class, () -> new ExternalIdentitySnapshot(identity(),
                null, null, null, Set.of()));
        assertThrows(NullPointerException.class, () -> new ExternalIdentitySnapshot(identity(),
                ExternalIdentitySnapshot.Status.ACTIVE, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentitySnapshot(identity(),
                ExternalIdentitySnapshot.Status.ACTIVE, null, null, Set.of(" ")));
        var groups = new HashSet<String>();
        groups.add(null);
        assertThrows(NullPointerException.class, () -> new ExternalIdentitySnapshot(identity(),
                ExternalIdentitySnapshot.Status.ACTIVE, null, null, groups));
    }

    @Test
    public void testResolutionOutcomes() {
        var resolved = new ExternalIdentityResolution.Resolved(identity());
        assertEquals(identity(), resolved.identity());
        assertInstanceOf(ExternalIdentityResolution.class, new ExternalIdentityResolution.Unhandled());
        assertEquals("Unknown actor", new ExternalIdentityResolution.Rejected("Unknown actor").reason());
        assertThrows(NullPointerException.class, () -> new ExternalIdentityResolution.Resolved(null));
        assertThrows(NullPointerException.class, () -> new ExternalIdentityResolution.Rejected(null));
        assertThrows(IllegalArgumentException.class, () -> new ExternalIdentityResolution.Rejected(" "));
    }

    @Test
    public void testLookupFailuresAreNotAuthoritativeResults() {
        var cause = new IllegalStateException("Authority unavailable");
        ExternalIdentityProvider provider = new ExternalIdentityProvider() {
            @Override
            public String getId() {
                return "provider";
            }

            @Override
            public ExternalIdentitySnapshot fetch(ExternalIdentityRef identity) throws ExternalIdentityException {
                throw new ExternalIdentityException("Lookup failed", cause);
            }
        };

        var failure = assertThrows(ExternalIdentityException.class, () -> provider.fetch(identity()));
        assertEquals("Lookup failed", failure.getMessage());
        assertSame(cause, failure.getCause());
    }

    private static ExternalIdentityRef identity() {
        return new ExternalIdentityRef("provider", "authority", "subject");
    }
}
