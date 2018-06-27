package com.walmartlabs.concord.server.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.server.agent.AgentCommand.Status;
import org.eclipse.sisu.EagerSingleton;
import org.jooq.Configuration;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.Timestamp;

import static com.walmartlabs.concord.server.jooq.tables.AgentCommands.AGENT_COMMANDS;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.field;

@Named
@EagerSingleton
public class AgentCommandWatchdog {

    private static final Logger log = LoggerFactory.getLogger(AgentCommandWatchdog.class);

    private static final long POLL_DELAY = 60 * 1000; // 1 min
    private static final long ERROR_DELAY = 3 * 60 * 1000; // 3 min

    private final WatchdogDao watchdogDao;

    @Inject
    public AgentCommandWatchdog(WatchdogDao watchdogDao) {
        this.watchdogDao = watchdogDao;
        init();
    }

    private void init() {
        new Thread(new Worker(POLL_DELAY, ERROR_DELAY, watchdogDao),
                "stalled-agent-commands-worker").start();
    }

    private static final class Worker implements Runnable {

        private final long interval;
        private final long errorInterval;
        private final WatchdogDao watchdogDao;

        private Worker(long interval, long errorInterval, WatchdogDao watchdogDao) {
            this.interval = interval;
            this.errorInterval = errorInterval;
            this.watchdogDao = watchdogDao;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Field<Timestamp> cutoff = currentTimestamp().minus(field("interval '10 minutes'"));
                    int n = watchdogDao.failStalled(cutoff);
                    log.info("run -> {} command(s) are timed out", n);
                    sleep(interval);
                } catch (Exception e) {
                    log.error("run -> error: {}", e.getMessage(), e);
                    sleep(errorInterval);
                }
            }
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Named
    private static final class WatchdogDao extends AbstractDao {

        @Inject
        public WatchdogDao(Configuration cfg) {
            super(cfg);
        }

        public int failStalled(Field<Timestamp> cutoff) {
            return txResult(tx -> tx.update(AGENT_COMMANDS)
                    .set(AGENT_COMMANDS.COMMAND_STATUS, Status.FAILED.toString())
                    .where(AGENT_COMMANDS.COMMAND_STATUS.in(Status.CREATED.toString())
                            .and(AGENT_COMMANDS.CREATED_AT.lessThan(cutoff)))
                    .execute());
        }
    }
}
