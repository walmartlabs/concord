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

import javax.inject.Named;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAITS;

@Named
public class WaitProcessStatusListener implements ProcessStatusListener {

    @Override
    public void onStatusChange(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        switch (status) {
            case NEW:
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
        tx.insertInto(PROCESS_WAITS)
                .set(PROCESS_WAITS.INSTANCE_ID, processKey.getInstanceId())
                .set(PROCESS_WAITS.INSTANCE_CREATED_AT, processKey.getCreatedAt())
                .execute();
    }

    private static void waiting(DSLContext tx, ProcessKey processKey) {
        tx.update(PROCESS_WAITS)
                .set(PROCESS_WAITS.IS_WAITING, true)
                .where(PROCESS_WAITS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAITS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())
                                .and(PROCESS_WAITS.WAIT_CONDITIONS.isNotNull())))
                .execute();
    }

    private static void clear(DSLContext tx, ProcessKey processKey) {
        tx.update(PROCESS_WAITS)
                .set(PROCESS_WAITS.IS_WAITING, false)
                .setNull(PROCESS_WAITS.WAIT_CONDITIONS)
                .where(PROCESS_WAITS.INSTANCE_ID.eq(processKey.getInstanceId())
                        .and(PROCESS_WAITS.INSTANCE_CREATED_AT.eq(processKey.getCreatedAt())
                                .and(PROCESS_WAITS.WAIT_CONDITIONS.isNotNull())))
                .execute();
    }
}
