package com.walmartlabs.concord.server.process.locks;

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
import com.walmartlabs.concord.server.Utils;
import com.walmartlabs.concord.server.jooq.tables.ProcessLocks;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.Configuration;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_LOCKS;
import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;

/**
 * Takes care of processes dead process locks.
 * E.g. removes locks for finished processes.
 */
public class ProcessLocksWatchdog implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessLocksWatchdog.class);

    private final WatchdogDao dao;

    @Inject
    public ProcessLocksWatchdog(@MainDB Configuration cfg) {
        this.dao = new WatchdogDao(cfg);
    }

    @Override
    public String getId() {
        return "process-locks-watchdog";
    }

    @Override
    public long getIntervalInSec() {
        // TODO cfg?
        return 5;
    }

    @Override
    public void performTask() {
        int count = dao.deleteStalledLocks();
        log.debug("performTask -> {} locks deleted", count);
    }

    private static final class WatchdogDao extends AbstractDao {

        private static final ProcessStatus[] FINISHED_STATUSES = {
                ProcessStatus.FINISHED,
                ProcessStatus.FAILED,
                ProcessStatus.CANCELLED,
                ProcessStatus.TIMED_OUT
        };

        public WatchdogDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public int deleteStalledLocks() {
            return txResult(tx -> {
                ProcessQueue q = PROCESS_QUEUE.as("q");
                ProcessLocks l = PROCESS_LOCKS.as("l");

                SelectConditionStep<Record1<UUID>> finishedProcesses = tx.select(q.INSTANCE_ID)
                        .from(q)
                        .where(q.INSTANCE_ID.eq(l.INSTANCE_ID)
                                .and(q.CURRENT_STATUS.in(Utils.toString(FINISHED_STATUSES))));

                return tx.deleteFrom(l)
                        .where(l.INSTANCE_ID.in(finishedProcesses))
                        .execute();
            });
        }
    }
}
