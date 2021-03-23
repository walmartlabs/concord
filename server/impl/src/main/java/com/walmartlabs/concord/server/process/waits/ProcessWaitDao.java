package com.walmartlabs.concord.server.process.waits;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.jooq.tables.records.ProcessWaitsRecord;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.UpdateSetMoreStep;

import javax.inject.Inject;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAITS;
import static org.jooq.impl.DSL.field;

public class ProcessWaitDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public ProcessWaitDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    @Override
    public void tx(Tx t) {
        super.tx(t);
    }

    public void updateWait(DSLContext tx, ProcessKey key, AbstractWaitCondition waits) {
        UpdateSetMoreStep<ProcessWaitsRecord> q = tx.update(PROCESS_WAITS)
                .set(PROCESS_WAITS.WAIT_CONDITIONS, field("?::jsonb", JSONB.class, objectMapper.toJSONB(waits)));

        if (waits == null) {
            q = q.set(PROCESS_WAITS.IS_WAITING, false);
        }

        q.where(PROCESS_WAITS.INSTANCE_ID.eq(key.getInstanceId())
                .and(PROCESS_WAITS.INSTANCE_CREATED_AT.eq(key.getCreatedAt())))
                .execute();
    }
}
