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
import com.walmartlabs.concord.server.jooq.tables.records.ProcessWaitConditionsRecord;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessWaitEntry;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.*;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.db.PgUtils.*;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
import static org.jooq.impl.DSL.*;

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

    // TODO: old process_queue.wait_conditions code, remove me (1.84.0 or later)
    public void updateWaitOld(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        tx.update(PROCESS_QUEUE)
                .set(PROCESS_QUEUE.WAIT_CONDITIONS, field("?::jsonb", JSONB.class, objectMapper.toJSONB(wait)))
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, currentOffsetDateTime())
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(processKey.getInstanceId()))
                .execute();
    }

    public void addWait(DSLContext tx, ProcessKey processKey, AbstractWaitCondition wait) {
        // TODO: old process_queue.wait_conditions code, remove me (1.84.0 or later)
        Field<JSONB> waitConditionsAsArray = when(jsonbTypeOf(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS).eq("object"), jsonbBuildArray(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS)).else_(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS);

        UUID instanceId = tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS,
                        jsonbAppend(jsonbOrEmptyArray(waitConditionsAsArray), objectMapper.toJSONB(wait)))
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .returning(PROCESS_WAIT_CONDITIONS.INSTANCE_ID)
                .fetchOptional()
                .map(ProcessWaitConditionsRecord::getInstanceId)
                .orElse(null);

        // TODO: remove me when `process.maxStateAge` exceed. old process (without conditions row)
        if (instanceId == null) {
            tx.insertInto(PROCESS_WAIT_CONDITIONS)
                    .columns(PROCESS_WAIT_CONDITIONS.INSTANCE_ID, PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT, PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS)
                    .values(processKey.getInstanceId(), processKey.getCreatedAt(), objectMapper.toJSONB(Collections.singletonList(wait), WAIT_LIST))
                    .execute();
        }
    }

    public void setWait(DSLContext tx, ProcessKey processKey, List<AbstractWaitCondition> waits, boolean isWaiting) {
        if (waits != null && waits.isEmpty()) {
            waits = null;
        }

        UUID instanceId = tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS, field("?::jsonb", JSONB.class, objectMapper.toJSONB(waits, WAIT_LIST)))
                .set(PROCESS_WAIT_CONDITIONS.IS_WAITING, isWaiting)
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .returning(PROCESS_WAIT_CONDITIONS.INSTANCE_ID)
                .fetchOptional()
                .map(ProcessWaitConditionsRecord::getInstanceId)
                .orElse(null);

        // TODO: remove me when `process.maxStateAge` exceed. old process (without conditions row)
        if (instanceId == null) {
            tx.insertInto(PROCESS_WAIT_CONDITIONS)
                    .columns(PROCESS_WAIT_CONDITIONS.INSTANCE_ID, PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT, PROCESS_WAIT_CONDITIONS.IS_WAITING, PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS)
                    .values(processKey.getInstanceId(), processKey.getCreatedAt(), isWaiting, objectMapper.toJSONB(waits, WAIT_LIST))
                    .execute();
        }
    }

    public ProcessWaitEntry get(ProcessKey processKey) {
        return txResult(tx -> {
            // TODO: old process_queue.wait_conditions code, remove me (1.84.0 or later)
            Field<JSONB> waitConditionsAsArray = when(jsonbTypeOf(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS).eq("object"), jsonbBuildArray(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS)).else_(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS);

           return tx.select(PROCESS_WAIT_CONDITIONS.IS_WAITING, waitConditionsAsArray)
                   .from(PROCESS_WAIT_CONDITIONS)
                   .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                           .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                   .fetchOne(r -> ProcessWaitEntry.of(r.value1(), objectMapper.fromJSONB(r.value2(), LIST_OF_MAP)));
        });
    }
}
