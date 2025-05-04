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

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
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
        HashCode hc = HashCode.fromBytes(key.getBytes());
        return Hashing.consistentHash(hc, cfg.getMaxAdvisoryLocks());
    }
}
