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

import com.walmartlabs.concord.server.process.queue.ProcessStatusListener;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
import static org.jooq.impl.DSL.*;

public class WaitProcessStatusListener implements ProcessStatusListener {

    @Override
    public void onStatusChange(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        switch (status) {
            case NEW:
            case PREPARING:
                init(tx, processKey);
                break;
            case WAITING:
            case SUSPENDED:
                waiting(tx, processKey);
                break;
            case FINISHED:
            case FAILED:
            case CANCELLED:
            case TIMED_OUT:
                clear(tx, processKey);
                break;
            default:
                // do nothing
        }
    }

    private static void init(DSLContext tx, ProcessKey processKey) {
        tx.insertInto(PROCESS_WAIT_CONDITIONS, PROCESS_WAIT_CONDITIONS.INSTANCE_ID, PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT)
                .select(
                        select(value(processKey.getInstanceId()), value(processKey.getCreatedAt()))
                                .whereNotExists(
                                        selectOne()
                                                .from(PROCESS_WAIT_CONDITIONS)
                                                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                                                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                                )
                )
                .execute();
    }

    private static void waiting(DSLContext tx, ProcessKey processKey) {
        tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.IS_WAITING, true)
                .set(PROCESS_WAIT_CONDITIONS.VERSION, PROCESS_WAIT_CONDITIONS.VERSION.plus(1))
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())
                                .and(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS.isNotNull())))
                .execute();
    }

    private static void clear(DSLContext tx, ProcessKey processKey) {
        tx.update(PROCESS_WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.IS_WAITING, false)
                .setNull(PROCESS_WAIT_CONDITIONS.WAIT_CONDITIONS)
                .set(PROCESS_WAIT_CONDITIONS.VERSION, PROCESS_WAIT_CONDITIONS.VERSION.plus(1))
                .where(PROCESS_WAIT_CONDITIONS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAIT_CONDITIONS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())))
                .execute();
    }
}
