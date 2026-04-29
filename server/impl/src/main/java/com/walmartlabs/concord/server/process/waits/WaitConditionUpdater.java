package com.walmartlabs.concord.server.process.waits;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

public class WaitConditionUpdater implements ProcessStatusListener {

    @Override
    public void onStatusChange(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        switch (status) {
            case SUSPENDED:
            case FINISHED:
            case FAILED:
            case CANCELLED:
            case TIMED_OUT:
                updateWaitConditions(tx, processKey, status);
                break;
            default:
                // do nothing
        }
    }

    private static void updateWaitConditions(DSLContext tx, ProcessKey processKey, ProcessStatus status) {
        String sql = """
            UPDATE process_wait_conditions
            SET wait_conditions =
                    (SELECT jsonb_agg(
                        CASE
                            WHEN obj->>'type' = 'PROCESS_COMPLETION' and obj->>'completeCondition' = 'ALL' and obj->>'processes' is not null
                                THEN jsonb_set(obj, '{processes}', (obj->'processes') - ?)
                            WHEN obj->>'type' = 'PROCESS_COMPLETION' and obj->>'completeCondition' = 'ONE_OF' and obj->>'processes' is not null and obj->'processes' ?? ?
                                THEN jsonb_set(obj, '{processes}', '[]')
                            ELSE obj
                        END
                     )
                     FROM jsonb_array_elements(wait_conditions) obj),
                version = version + 1
            WHERE wait_conditions @> ?::jsonb;
            """;

        String jsonMatch = String.format("[{\"type\": \"PROCESS_COMPLETION\", \"finalStatuses\": [\"%s\"],  \"processes\": [\"%s\"]}]", status.name(), processKey.getInstanceId().toString());

        tx.execute(sql, processKey.getInstanceId().toString(), processKey.getInstanceId().toString(), jsonMatch);
    }
}
