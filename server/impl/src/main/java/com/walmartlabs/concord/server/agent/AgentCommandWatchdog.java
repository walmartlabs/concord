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
import com.walmartlabs.concord.server.task.ScheduledTask;
import org.jooq.Configuration;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.server.jooq.tables.AgentCommands.AGENT_COMMANDS;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.field;

@Named("agent-command-watchdog")
@Singleton
public class AgentCommandWatchdog implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(AgentCommandWatchdog.class);

    private final WatchdogDao watchdogDao;

    @Inject
    public AgentCommandWatchdog(WatchdogDao watchdogDao) {
        this.watchdogDao = watchdogDao;
    }

    @Override
    public long getIntervalInSec() {
        return TimeUnit.MINUTES.toSeconds(1);
    }

    @Override
    public void performTask() {
        Field<Timestamp> cutoff = currentTimestamp().minus(field("interval '10 minutes'"));
        int n = watchdogDao.failStalled(cutoff);
        log.info("run -> {} command(s) are timed out", n);
    }

    @Named
    private static final class WatchdogDao extends AbstractDao {

        @Inject
        public WatchdogDao(@Named("app") Configuration cfg) {
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
