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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.agent.AgentCommand.Status;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.Configuration;
import org.jooq.Field;

import javax.inject.Inject;
import java.time.OffsetDateTime;

import static com.walmartlabs.concord.server.jooq.tables.AgentCommands.AGENT_COMMANDS;

public class AgentCommandWatchdog implements ScheduledTask {

    private final AgentConfiguration cfg;

    private final WatchdogDao watchdogDao;

    @Inject
    public AgentCommandWatchdog(AgentConfiguration cfg, WatchdogDao watchdogDao) {
        this.cfg = cfg;
        this.watchdogDao = watchdogDao;
    }

    @Override
    public String getId() {
        return "agent-command-watchdog";
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getWatchdogPeriod().getSeconds();
    }

    @Override
    public void performTask() {
        watchdogDao.failStalled(PgUtils.nowMinus(cfg.getMaxStalledAge()));
        watchdogDao.cleanupOld(PgUtils.nowMinus(cfg.getMaxCommandAge()));
    }

    private static final class WatchdogDao extends AbstractDao {

        @Inject
        public WatchdogDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public int failStalled(Field<OffsetDateTime> cutoff) {
            return txResult(tx -> tx.update(AGENT_COMMANDS)
                    .set(AGENT_COMMANDS.COMMAND_STATUS, Status.FAILED.toString())
                    .where(AGENT_COMMANDS.COMMAND_STATUS.in(Status.CREATED.toString())
                            .and(AGENT_COMMANDS.CREATED_AT.lessThan(cutoff)))
                    .execute());
        }

        public int cleanupOld(Field<OffsetDateTime> cutoff) {
            return txResult(tx -> tx.deleteFrom(AGENT_COMMANDS)
                    .where(AGENT_COMMANDS.COMMAND_STATUS.in(Status.SENT.toString(), Status.FAILED.toString())
                            .and(AGENT_COMMANDS.CREATED_AT.lessThan(cutoff)))
                    .execute());
        }
    }
}
