package com.walmartlabs.concord.server;

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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocksTest {

    /**
     * Expected values were produced by Guava's {@code Hashing.consistentHash(HashCode.fromBytes(key), buckets)},
     * which earlier server versions used. Lock IDs must not change between versions.
     */
    @Test
    void lockIdsMatchPreviousImplementation() {
        assertLockId("a", 16, 0);
        assertLockId("process-queue", 16, 12);
        assertLockId("orgName/projectName", 16, 7);
        assertLockId("привет", 16, 15);
        assertLockId("0123456789abcdef-long-key", 16, 11);

        assertLockId("a", 1, 0);
        assertLockId("a", 64, 29);
        assertLockId("process-queue", 32768, 20232);
        assertLockId("orgName/projectName", 32768, 11275);
        assertLockId("привет", 64, 37);
        assertLockId("0123456789abcdef-long-key", Integer.MAX_VALUE, 1210241228);
        assertLockId("process-queue", Integer.MAX_VALUE, 989002381);
    }

    @Test
    void emptyKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Locks.padToLong(new byte[0]));
    }

    private static void assertLockId(String key, int buckets, int expected) {
        long input = Locks.padToLong(key.getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, Locks.consistentHash(input, buckets), key + " with " + buckets + " buckets");
    }
}
