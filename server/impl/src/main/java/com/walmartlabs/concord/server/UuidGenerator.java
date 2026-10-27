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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Generates UUID v7 values.
 */
public class UuidGenerator {

    private static final long RANDOM_LOW_MASK = 0x3fff_ffff_ffff_ffffL;

    private final Random random;
    private final LongSupplier clock;
    private final byte[] entropy = new byte[10];
    private boolean initialized;
    private long lastTimestamp;
    private int randomHigh;
    private long randomLow;

    public UuidGenerator() {
        this(strongRandom(), System::currentTimeMillis);
    }

    UuidGenerator(Random random, LongSupplier clock) {
        this.random = random;
        this.clock = clock;
    }

    private static SecureRandom strongRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized UUID generate() {
        var timestamp = clock.getAsLong();
        // A changed clock value, including a rollback, starts a fresh random
        // sequence using the actual timestamp, as in the previous generator.
        if (!initialized || timestamp != lastTimestamp) {
            random.nextBytes(entropy);
            randomHigh = ((entropy[0] & 0x0f) << 8) | (entropy[1] & 0xff);
            randomLow = 0;
            for (int i = 2; i < entropy.length; i++) {
                randomLow = (randomLow << 8) | (entropy[i] & 0xffL);
            }
            randomLow &= RANDOM_LOW_MASK;
            lastTimestamp = timestamp;
            initialized = true;
        } else if (randomLow != RANDOM_LOW_MASK) {
            // Increment only the 74 bits that survive the version/variant masks.
            randomLow++;
        } else if (randomHigh != 0xfff) {
            randomHigh++;
            randomLow = 0;
        } else {
            throw new IllegalStateException("overflow on same millisecond");
        }

        return new UUID((timestamp << 16) | 0x7000L | randomHigh,
                0x8000_0000_0000_0000L | randomLow);
    }
}
