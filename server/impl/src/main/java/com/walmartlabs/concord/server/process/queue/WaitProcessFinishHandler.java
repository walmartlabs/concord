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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.ProcessStatus;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;

@Named
@Singleton
public class WaitProcessFinishHandler implements ProcessWaitHandler<ProcessCompletionCondition> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Dao dao;

    @Inject
    public WaitProcessFinishHandler(Dao dao) {
        this.dao = dao;
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_COMPLETION;
    }

    @Override
    public ProcessCompletionCondition process(UUID instanceId, ProcessCompletionCondition wait) {
        List<UUID> awaitProcesses = wait.getProcesses();
        List<UUID> finishedProcesses = dao.findFinished(awaitProcesses);
        if (finishedProcesses.isEmpty()) {
            return wait;
        }

        List<UUID> processes = new ArrayList<>(awaitProcesses);
        processes.removeAll(finishedProcesses);
        if (processes.isEmpty()) {
            return null;
        }

        return new ProcessCompletionCondition(wait.getReason(), processes);
    }

    private static class Payload {

        private final List<UUID> processes;

        @JsonCreator
        private Payload(@JsonProperty("processes") List<UUID> processes) {
            this.processes = processes;
        }
    }

    @Named
    private static final class Dao extends AbstractDao {

        private static final Set<ProcessStatus> FINISHED_STATUSES = ImmutableSet.of(
                ProcessStatus.FINISHED,
                ProcessStatus.FAILED,
                ProcessStatus.CANCELLED,
                ProcessStatus.TIMED_OUT);

        @Inject
        public Dao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public List<UUID> findFinished(List<UUID> awaitProcesses) {
            return txResult(tx -> {
                ProcessQueue q = PROCESS_QUEUE.as("q");
                return tx.select(q.INSTANCE_ID)
                        .from(q)
                        .where(q.INSTANCE_ID.in(awaitProcesses)
                                .and(q.CURRENT_STATUS.in(FINISHED_STATUSES)))
                        .fetch(q.INSTANCE_ID);
            });
        }
    }
}
