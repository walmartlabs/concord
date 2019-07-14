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

import com.walmartlabs.concord.server.process.pipelines.processors.ExclusiveGroupProcessor;
import org.jooq.DSLContext;

import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.CallableStatement;

/**
 * A global lock used by the "exclusive trigger" check.
 * @see ExclusiveGroupProcessor
 */
@Named
@Singleton
public class ExclusiveGroupLock {

    private static final long LOCK_KEY = 1562319227723L;

    public void lock(DSLContext tx) {

        String sql = "{ call pg_advisory_xact_lock(?) }";

        tx.connection(conn -> {
            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.setLong(1, LOCK_KEY);
                cs.execute();
            }
        });
    }
}
