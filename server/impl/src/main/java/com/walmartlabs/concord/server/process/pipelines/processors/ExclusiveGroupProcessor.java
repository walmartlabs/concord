package com.walmartlabs.concord.server.process.pipelines.processors;

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
import com.walmartlabs.concord.runtime.v2.model.ExclusiveModeConfiguration;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadUtils;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ExclusiveGroupLock;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.db.PgUtils.jsonbText;
import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_QUEUE;
import static org.jooq.impl.DSL.*;

/**
 * If the process has an "exclusive group and mode cancel/cancelOld" assigned to it, this processor
 * determines whether the process can continue to run or should be cancelled.
 * The processor uses a global (DB) lock {@link ExclusiveGroupLock}.
 */
@Named
public class ExclusiveGroupProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;
    private final Map<ExclusiveModeConfiguration.Mode, ModeProcessor> processors;

    @Inject
    public ExclusiveGroupProcessor(ProcessLogManager logManager,
                                   List<ModeProcessor> processors) {

        this.logManager = logManager;
        this.processors = processors.stream().collect(Collectors.toMap(ModeProcessor::mode, v -> v));
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        ExclusiveModeConfiguration exclusive = PayloadUtils.getExclusive(payload);
        if (exclusive == null) {
            return chain.process(payload);
        }

        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return chain.process(payload);
        }

        ModeProcessor processor = processors.get(exclusive.mode());
        if (processor == null) {
            return chain.process(payload);
        }

        logManager.info(payload.getProcessKey(), "Process' exclusive group: {}", exclusive.group());

        boolean canContinue = processor.process(payload, exclusive);
        if (canContinue) {
            return chain.process(payload);
        }

        return payload;
    }

    interface ModeProcessor {

        boolean process(Payload payload, ExclusiveModeConfiguration exclusive);

        ExclusiveModeConfiguration.Mode mode();
    }

    @Named
    static class CancelModeProcessor implements ModeProcessor {

        private final CancelModeDao dao;
        private final ExclusiveGroupLock exclusiveGroupLock;
        private final ProcessLogManager logManager;
        private final ProcessQueueManager queueManager;

        @Inject
        public CancelModeProcessor(CancelModeDao dao,
                                   ExclusiveGroupLock exclusiveGroupLock,
                                   ProcessLogManager logManager,
                                   ProcessQueueManager queueManager) {
            this.dao = dao;
            this.exclusiveGroupLock = exclusiveGroupLock;
            this.logManager = logManager;
            this.queueManager = queueManager;
        }

        @Override
        public boolean process(Payload payload, ExclusiveModeConfiguration exclusive) {
            ProcessKey processKey = payload.getProcessKey();
            UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
            UUID projectId = payload.getHeader(Payload.PROJECT_ID);

            return dao.txResult(tx -> {
                exclusiveGroupLock.lock(tx);

                if (dao.exists(tx, processKey.getInstanceId(), parentInstanceId, projectId, exclusive.group())) {
                    logManager.warn(processKey, "Process(es) with exclusive group '" + exclusive.group() + "' is already in the queue. " +
                            "Current process has been cancelled");

                    queueManager.updateStatus(tx, processKey, ProcessStatus.CANCELLED);
                    return false;
                }

                queueManager.updateExclusive(tx, processKey, exclusive);

                return true;
            });
        }

        @Override
        public ExclusiveModeConfiguration.Mode mode() {
            return ExclusiveModeConfiguration.Mode.cancel;
        }
    }

    @Named
    static class CancelOldModeProcessor implements ModeProcessor {

        private static final String CANCELLED_MSG = "Process '{}' with exclusive group '{}' is in the queue. Current process has been cancelled";

        private final CancelOldModeDao dao;
        private final AgentManager agentManager;
        private final ProcessLogManager logManager;
        private final ExclusiveGroupLock exclusiveGroupLock;
        private final ProcessQueueManager queueManager;

        @Inject
        public CancelOldModeProcessor(CancelOldModeDao dao,
                                      AgentManager agentManager,
                                      ProcessLogManager logManager,
                                      ExclusiveGroupLock exclusiveGroupLock,
                                      ProcessQueueManager queueManager) {
            this.dao = dao;
            this.agentManager = agentManager;
            this.logManager = logManager;
            this.exclusiveGroupLock = exclusiveGroupLock;
            this.queueManager = queueManager;
        }

        @Override
        public boolean process(Payload payload, ExclusiveModeConfiguration exclusive) {
            ProcessKey processKey = payload.getProcessKey();
            UUID parentInstanceId = payload.getHeader(Payload.PARENT_INSTANCE_ID);
            UUID projectId = payload.getHeader(Payload.PROJECT_ID);

            Result result = dao.txResult(tx -> {
                exclusiveGroupLock.lock(tx);

                List<ProcessKey> processes = dao.listOld(tx, processKey, parentInstanceId, projectId, exclusive.group());
                agentManager.killProcess(processes);

                ProcessKey newProcess = dao.anyNew(tx, processKey, projectId, exclusive.group());
                if (newProcess != null) {
                    agentManager.killProcess(processKey);
                }

                queueManager.updateExclusive(tx, processKey, exclusive);

                return new Result(processes, newProcess);
            });

            result.oldProcesses().forEach(pk -> logManager.warn(pk, CANCELLED_MSG, payload.getProcessKey(), exclusive.group()));

            if (result.newProcess() != null) {
                logManager.warn(processKey, CANCELLED_MSG, result.newProcess(), exclusive.group());
            }

            return result.newProcess() == null;
        }

        @Override
        public ExclusiveModeConfiguration.Mode mode() {
            return ExclusiveModeConfiguration.Mode.cancelOld;
        }

        private static class Result {

            private final List<ProcessKey> oldProcesses;
            private final ProcessKey newProcess;

            private Result(List<ProcessKey> oldProcesses, ProcessKey newProcess) {
                this.oldProcesses = oldProcesses;
                this.newProcess = newProcess;
            }

            public List<ProcessKey> oldProcesses() {
                return oldProcesses;
            }

            public ProcessKey newProcess() {
                return newProcess;
            }
        }
    }

    @Named
    static class CancelModeDao extends AbstractDao  {

        private static final List<ProcessStatus> RUNNING_STATUSES = Arrays.asList(
                ProcessStatus.NEW,
                ProcessStatus.PREPARING,
                ProcessStatus.ENQUEUED,
                ProcessStatus.STARTING,
                ProcessStatus.RUNNING,
                ProcessStatus.SUSPENDED,
                ProcessStatus.RESUMING);

        @Inject
        protected CancelModeDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        public boolean exists(DSLContext tx, UUID currentInstanceId, UUID parentInstanceId, UUID projectId, String exclusiveGroup) {
            SelectConditionStep<Record1<Integer>> s = tx.selectOne()
                            .from(PROCESS_QUEUE)
                            .where(jsonbText(PROCESS_QUEUE.EXCLUSIVE, "group").eq(exclusiveGroup)
                                    .and(PROCESS_QUEUE.PROJECT_ID.eq(projectId)
                                            .and(PROCESS_QUEUE.INSTANCE_ID.notEqual(currentInstanceId)
                                                    .and(PROCESS_QUEUE.CURRENT_STATUS.in(RUNNING_STATUSES)))));

            // parent's
            if (parentInstanceId != null) {
                s.and(PROCESS_QUEUE.INSTANCE_ID.notIn(parents(tx, parentInstanceId)));
            }

            return tx.fetchExists(s);
        }
    }

    @Named
    static class CancelOldModeDao extends AbstractDao  {

        private static final List<ProcessStatus> RUNNING_STATUSES = Arrays.asList(
                ProcessStatus.NEW,
                ProcessStatus.PREPARING,
                ProcessStatus.ENQUEUED,
                ProcessStatus.STARTING,
                ProcessStatus.RUNNING,
                ProcessStatus.SUSPENDED,
                ProcessStatus.RESUMING);

        @Inject
        protected CancelOldModeDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        @Override
        public <T> T txResult(TxResult<T> t) {
            return super.txResult(t);
        }

        public List<ProcessKey> listOld(DSLContext tx, ProcessKey currentProcessKey, UUID parentInstanceId, UUID projectId, String exclusiveGroup) {
            SelectConditionStep<Record2<UUID, OffsetDateTime>> s = tx.select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT)
                    .from(PROCESS_QUEUE)
                    .where(jsonbText(PROCESS_QUEUE.EXCLUSIVE, "group").eq(exclusiveGroup)
                            .and(PROCESS_QUEUE.PROJECT_ID.eq(projectId)
                                    .and(PROCESS_QUEUE.INSTANCE_ID.notEqual(currentProcessKey.getInstanceId())
                                            .and(PROCESS_QUEUE.CREATED_AT.lessOrEqual(currentProcessKey.getCreatedAt())
                                                    .and(PROCESS_QUEUE.CURRENT_STATUS.in(RUNNING_STATUSES))))));

            // parent's
            if (parentInstanceId != null) {
                s.and(PROCESS_QUEUE.INSTANCE_ID.notIn(parents(tx, parentInstanceId)));
            }

            return s.fetch(r -> new ProcessKey(r.value1(), r.value2()));
        }

        public ProcessKey anyNew(DSLContext tx, ProcessKey currentProcessKey, UUID projectId, String exclusiveGroup) {
            SelectConditionStep<Record2<UUID, OffsetDateTime>> s = tx.select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT)
                    .from(PROCESS_QUEUE)
                    .where(jsonbText(PROCESS_QUEUE.EXCLUSIVE, "group").eq(exclusiveGroup)
                            .and(PROCESS_QUEUE.PROJECT_ID.eq(projectId)
                                    .and(PROCESS_QUEUE.CREATED_AT.greaterOrEqual(currentProcessKey.getCreatedAt()))));

            SelectJoinStep<Record1<UUID>> children = tx.withRecursive("children").as(
                    select(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID, ProcessQueue.PROCESS_QUEUE.PARENT_INSTANCE_ID).from(ProcessQueue.PROCESS_QUEUE)
                            .where(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID.eq(currentProcessKey.getInstanceId()))
                            .unionAll(
                                    select(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID, ProcessQueue.PROCESS_QUEUE.PARENT_INSTANCE_ID)
                                            .from(ProcessQueue.PROCESS_QUEUE)
                                            .join(name("children"))
                                            .on(ProcessQueue.PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(
                                                    field(name("children", "INSTANCE_ID"), UUID.class)))))
                    .select(field("children.INSTANCE_ID", UUID.class))
                    .from(name("children"));

            s.and(PROCESS_QUEUE.INSTANCE_ID.notIn(children));

            return s.limit(1)
                    .fetchOne(r -> new ProcessKey(r.value1(), r.value2()));
        }
    }

    private static SelectJoinStep<Record1<UUID>> parents(DSLContext tx, UUID parentInstanceId) {
        return tx.withRecursive("parents").as(
                select(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID, ProcessQueue.PROCESS_QUEUE.PARENT_INSTANCE_ID).from(ProcessQueue.PROCESS_QUEUE)
                        .where(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID.eq(parentInstanceId))
                        .unionAll(
                                select(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID, ProcessQueue.PROCESS_QUEUE.PARENT_INSTANCE_ID)
                                        .from(ProcessQueue.PROCESS_QUEUE)
                                        .join(name("parents"))
                                        .on(ProcessQueue.PROCESS_QUEUE.INSTANCE_ID.eq(
                                                field(name("parents", "PARENT_INSTANCE_ID"), UUID.class)))))
                .select(field("parents.INSTANCE_ID", UUID.class))
                .from(name("parents"));
    }
}
