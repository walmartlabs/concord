package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.LockingConfiguration;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.sql.CallableStatement;

/**
 * Locking mechanism based on DB (advisory) locks
 */
public class Locks {

    private static final String LOCK_SQL = "{ call pg_advisory_xact_lock(?) }";

    private final LockingConfiguration cfg;

    @Inject
    public Locks(LockingConfiguration cfg) {
        this.cfg = cfg;
    }

    public void lock(DSLContext tx, String key) {
        lock(tx, hash(key));
    }

    @WithTimer
    public void lock(DSLContext tx, long key) {
        tx.connection(conn -> {
            try (CallableStatement cs = conn.prepareCall(LOCK_SQL)) {
                cs.setLong(1, key);
                cs.execute();
            }
        });
    }

    private long hash(String key) {
        return consistentHash(padToLong(key.getBytes()), cfg.getMaxAdvisoryLocks());
    }

    /**
     * Reads up to the first eight bytes as a little-endian long, zero-padded.
     * Matches Guava's {@code HashCode.fromBytes(bytes).padToLong()}, which the lock IDs used to be based on.
     */
    static long padToLong(byte[] bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("A lock key must not be empty");
        }

        long result = 0;
        for (int i = 0; i < Math.min(bytes.length, Long.BYTES); i++) {
            result |= (bytes[i] & 0xFFL) << (i * 8);
        }
        return result;
    }

    /**
     * "Jump" consistent hash (Lamping and Veach, https://arxiv.org/abs/1406.2294),
     * using the same linear congruential generator as Guava's {@code Hashing.consistentHash},
     * so the lock IDs stay identical across server versions.
     */
    static int consistentHash(long input, int buckets) {
        if (buckets <= 0) {
            throw new IllegalArgumentException("buckets must be positive: " + buckets);
        }

        long state = input;
        int candidate = 0;
        while (true) {
            state = 2862933555777941757L * state + 1;
            double next = (double) ((int) (state >>> 33) + 1) / 0x1.0p31;
            int candidateNext = (int) ((candidate + 1) / next);
            if (candidateNext >= 0 && candidateNext < buckets) {
                candidate = candidateNext;
            } else {
                return candidate;
            }
        }
    }
}
