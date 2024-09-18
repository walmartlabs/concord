package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessStatusListener;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Updates the total running time of a process when it transitions to a terminal (or suspended) state.
 */
public class TotalRuntimeCalculator implements ProcessStatusListener {

    private final ProcessQueueDao dao;

    @Inject
    public TotalRuntimeCalculator(ProcessQueueDao dao) {
        this.dao = dao;
    }

    @Override
    public void onStatusChange(DSLContext tx, ProcessKey processKey, ProcessStatus status) {

        switch (status) {
            case FINISHED, FAILED, CANCELLED, SUSPENDED, TIMED_OUT -> dao.getLastRunAt(tx, processKey)
                    .ifPresent(lastRunAt -> {
                        Duration duration = Duration.between(lastRunAt, OffsetDateTime.now());
                        dao.addToTotalRunningTime(tx, processKey, duration);
                    });
        }
    }
}
