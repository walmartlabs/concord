package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class UuidGeneratorTest {

    @Test
    void encodesTimestampVersionVariantAndEntropy() {
        var random = new FixedRandom((byte) 0xab);
        var generator = new UuidGenerator(random, () -> 0x123456789abcL);

        var id = generator.generate();
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
        assertEquals(0x123456789abcL, id.getMostSignificantBits() >>> 16);
        assertEquals(UUID.fromString("12345678-9abc-7bab-abab-abababababab"), id);
    }

    @Test
    void incrementsAcrossVariantBitsWithoutDuplicates() {
        var generator = new UuidGenerator(new FixedRandom((byte) 0xff) {
            @Override
            public void nextBytes(byte[] bytes) {
                super.nextBytes(bytes);
                bytes[0] = 0;
                bytes[1] = 0;
            }
        }, () -> 42);

        var first = generator.generate();
        var second = generator.generate();
        assertEquals(UUID.fromString("00000000-002a-7000-bfff-ffffffffffff"), first);
        assertEquals(UUID.fromString("00000000-002a-7001-8000-000000000000"), second);
        assertTrue(first.compareTo(second) < 0);
    }

    @Test
    void rollbackUsesActualTimestampAndFreshEntropy() {
        var clock = new AtomicLong(100);
        var random = new Random() {
            private byte next;

            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, next++);
            }
        };
        var generator = new UuidGenerator(random, clock::get);
        var first = generator.generate();
        clock.set(99);
        var rolledBack = generator.generate();
        clock.set(100);
        var returned = generator.generate();

        assertEquals(100, first.getMostSignificantBits() >>> 16);
        assertEquals(99, rolledBack.getMostSignificantBits() >>> 16);
        assertEquals(100, returned.getMostSignificantBits() >>> 16);
        assertEquals(0x101, rolledBack.getMostSignificantBits() & 0xfff);
        assertEquals(0x202, returned.getMostSignificantBits() & 0xfff);
        assertNotEquals(first, returned);
    }

    @Test
    void overflowRemainsAnErrorUntilTimestampChanges() {
        var clock = new AtomicLong(10);
        var generator = new UuidGenerator(new FixedRandom((byte) 0xff), clock::get);
        generator.generate();
        assertThrows(IllegalStateException.class, generator::generate);
        assertThrows(IllegalStateException.class, generator::generate);
        clock.incrementAndGet();
        assertEquals(11, generator.generate().getMostSignificantBits() >>> 16);
    }

    @Test
    void concurrentCallsWithinOneMillisecondAreUnique() throws Exception {
        var generator = new UuidGenerator(new FixedRandom((byte) 0), () -> 123);
        var ids = ConcurrentHashMap.<UUID>newKeySet();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = new Future<?>[8];
            for (int i = 0; i < futures.length; i++) {
                futures[i] = executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < 1000; j++) {
                        ids.add(generator.generate());
                    }
                    return null;
                });
            }
            start.countDown();
            for (var future : futures) {
                future.get();
            }
            assertEquals(8000, ids.size());
        } finally {
            executor.shutdownNow();
        }
    }

    private static class FixedRandom extends Random {
        private final byte value;

        private FixedRandom(byte value) {
            this.value = value;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, value);
        }
    }
}
