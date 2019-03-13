package com.walmartlabs.concord.server.process.queue;

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

import org.jooq.DSLContext;

import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.CallableStatement;
import java.sql.Types;

@Named
@Singleton
public class ProcessQueueLock {

    private static final long LOCK_KEY = 1552468327245L;

    public boolean tryLock(DSLContext tx) {
        String sql = "{ ? = call pg_try_advisory_xact_lock(?) }";

        return tx.connectionResult(conn -> {
            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.registerOutParameter(1, Types.BOOLEAN);
                cs.setLong(2, LOCK_KEY);
                cs.execute();
                return cs.getBoolean(1);
            }
        });
    }
}
