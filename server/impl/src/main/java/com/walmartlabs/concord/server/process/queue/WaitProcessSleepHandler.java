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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.Configuration;
import org.jooq.Field;
import org.jooq.Record1;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_QUEUE;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.field;

/**
 * Handles the processes that are waiting for some timeout. Resumes a suspended process
 * if the timeout exceeded.
 */
@Named
@Singleton
public class WaitProcessSleepHandler implements ProcessWaitHandler<ProcessSleepCondition> {

    private static final Set<ProcessStatus> STATUSES = Collections.singleton(ProcessStatus.SUSPENDED);

    private final ProcessSleepDao processSleepDao;

    @Inject
    public WaitProcessSleepHandler(ProcessSleepDao processSleepDao) {
        this.processSleepDao = processSleepDao;
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_SLEEP;
    }

    @Override
    public Set<ProcessStatus> getProcessStatuses() {
        return STATUSES;
    }

    @Override
    public Result<ProcessSleepCondition> process(ProcessKey key, ProcessStatus status, ProcessSleepCondition wait) {
        if (processSleepDao.isSleepFinished(key.getInstanceId())) {
            return Result.of(wait.resumeEvent());
        }

        return Result.of(wait);
    }

    @Named
    private static final class ProcessSleepDao extends AbstractDao {

        @Inject
        protected ProcessSleepDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public boolean isSleepFinished(UUID instanceId) {
            Field<Timestamp> untilField = field("({0}->>'until')::timestamptz", Timestamp.class, PROCESS_QUEUE.WAIT_CONDITIONS);
            return txResult(tx -> {
                Record1<Integer> result = tx.selectOne()
                        .from(PROCESS_QUEUE)
                        .where(PROCESS_QUEUE.INSTANCE_ID.eq(instanceId)
                                .and(PROCESS_QUEUE.WAIT_CONDITIONS.isNotNull()
                                        .and(currentTimestamp().greaterOrEqual(untilField))))
                        .fetchOne();
                return result != null;
            });
        }
    }
}
