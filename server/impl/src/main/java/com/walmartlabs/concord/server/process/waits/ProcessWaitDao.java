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

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessWaitEntry;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.JSONB;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.db.PgUtils.jsonbAppend;
import static com.walmartlabs.concord.db.PgUtils.jsonbOrEmptyArray;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
import static org.jooq.impl.DSL.field;

public class ProcessWaitDao extends AbstractDao {

    public static final TypeReference<List<AbstractWaitCondition>> WAIT_LIST = new TypeReference<List<AbstractWaitCondition>>() {
    };

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP = new TypeReference<List<Map<String, Object>>>() {
    };

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

    @Override
    public <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public void addWait(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.VERSION, PROCESS_WAIT_CONDITIONS.VERSION.plus(1))
                .set(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS,
                        jsonbAppend(jsonbOrEmptyArray(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS), objectMapper.toJSONB(wait)))
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .execute();
    }

    public boolean setWait(DSLContext tx, ProcessKey processKey, List<AbstractWaitCondition> waits, boolean isWaiting, long version) {
        if (waits != null && waits.isEmpty()) {
            waits = null;
        }

        int rows = tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS, field("?::jsonb", JSONB.class, objectMapper.toJSONB(waits, WAIT_LIST)))
                .set(PROCESS_WAIT_CONDITIONS.IS_WAITING, isWaiting)
                .set(PROCESS_WAIT_CONDITIONS.VERSION, PROCESS_WAIT_CONDITIONS.VERSION.plus(1))
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())
                                .and(PROCESS_WAIT_CONDITIONS.VERSION.eq(version))))
                .execute();

        return rows > 0;
    }

    public ProcessWaitEntry get(ProcessKey processKey) {
        return txResult(tx -> tx.select(PROCESS_WAIT_CONDITIONS.IS_WAITING, PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS)
                .from(PROCESS_WAIT_CONDITIONS)
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .fetchOne(r -> ProcessWaitEntry.of(r.value1(), objectMapper.fromJSONB(r.value2(), LIST_OF_MAP))));
    }
}
