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
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.jooq.*;

import javax.inject.Inject;

import java.util.List;

import static com.walmartlabs.concord.db.PgUtils.*;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
import static org.jooq.impl.DSL.*;

public class ProcessWaitDao extends AbstractDao {

    private static final TypeReference<List<AbstractWaitCondition>> WAIT_LIST = new TypeReference<List<AbstractWaitCondition>>() {
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

    public void addWait(DSLContext tx, ProcessKey key, AbstractWaitCondition wait) {
        // TODO: remove me in next version (after all wait conditions is arrays)
        Field<JSONB> waitConditionsAsArray = when(jsonbTypeOf(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS).eq("object"), jsonbBuildArray(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS)).else_(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS);

        tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS,
                        jsonbAppend(jsonbOrEmptyArray(waitConditionsAsArray), objectMapper.toJSONB(wait)))
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(key.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(key.getCreatedAt())))
                .execute();
    }

    public void setWait(DSLContext tx, ProcessKey key, List<AbstractWaitCondition> waits) {
        if (waits != null && waits.isEmpty()) {
            waits = null;
        }

        UpdateSetMoreStep<?> q = tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS, field("?::jsonb", JSONB.class, objectMapper.toJSONB(waits, WAIT_LIST)));

        if (waits == null) {
            q = q.set(PROCESS_WAIT_CONDITIONS.IS_WAITING, false);
        }

        q.where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(key.getInstanceId())
                .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(key.getCreatedAt())))
                .execute();
    }
}
