package com.walmartlabs.concord.server.process.waits;

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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.cfg.ProcessWaitWatchdogConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessWaitConditions;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.JSONB;
import org.jooq.Record5;
import org.jooq.SelectConditionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;

/**
 * Takes care of processes with wait conditions.
 * E.g. waiting for other processes to finish, locking, etc.
 */
public class ProcessWaitWatchdog implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ProcessWaitWatchdog.class);

    private final ProcessWaitWatchdogConfiguration cfg;
    private final WatchdogDao dao;
    private final ProcessWaitManager processWaitManager;
    private final ProcessManager processManager;
    private final PayloadManager payloadManager;
    private final Map<WaitType, ProcessWaitHandler<AbstractWaitCondition>> processWaitHandlers;
    private final Histogram waitItemsHistogram;

    @Inject
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ProcessWaitWatchdog(ProcessWaitWatchdogConfiguration cfg,
                               @MainDB Configuration dbCfg,
                               ConcordObjectMapper objectMapper,
                               ProcessWaitManager processWaitManager,
                               ProcessManager processManager,
                               PayloadManager payloadManager,
                               Set<ProcessWaitHandler> handlers,
                               MetricRegistry metricRegistry) {

        this.cfg = cfg;
        this.dao = new WatchdogDao(dbCfg, objectMapper);
        this.processWaitManager = processWaitManager;
        this.processManager = processManager;
        this.payloadManager = payloadManager;
        this.processWaitHandlers = new EnumMap<>(WaitType.class);

        handlers.forEach(h -> this.processWaitHandlers.put(h.getType(), h));
        this.waitItemsHistogram = metricRegistry.histogram("process-wait-watchdog-items");
    }

    @Override
    public String getId() {
        return "process-wait-watchdog";
    }

    @Override
    public long getIntervalInSec() {
        return cfg.getPeriod().getSeconds();
    }

    @Override
    @WithTimer
    public void performTask() {
        Long lastId = null;
        while (true) {
            List<WaitingProcess> processes = dao.nextWaitItems(lastId, cfg.getPollLimit());
            waitItemsHistogram.update(processes.size());

            if (processes.isEmpty()) {
                return;
            }

            for (WaitingProcess p : processes) {
                processWaits(p);
                lastId = p.id();
            }

            if (processes.size() < cfg.getPollLimit()) {
                return;
            }
        }
    }

    @WithTimer
    void processWaits(WaitingProcess p) {
        Set<String> resumeEvents = new HashSet<>();
        List<AbstractWaitCondition> resultWaits = new ArrayList<>();
        List<ProcessWaitHandler.Action> resultActions = new ArrayList<>();

        boolean hasExclusive = p.waits().stream().anyMatch(AbstractWaitCondition::exclusive);

        for (AbstractWaitCondition w : p.waits()) {
            ProcessWaitHandler.Result<AbstractWaitCondition> result;
            if (!hasExclusive || w.exclusive()) {
                result = processWait(w, p);
            } else {
                result = ProcessWaitHandler.Result.of(w);
            }

            if (result != null) {
                if (result.waitCondition() != null) {
                    resultWaits.add(result.waitCondition());
                } else if (result.resumeEvent() != null) {
                    resumeEvents.add(result.resumeEvent());
                }

                if (result.action() != null) {
                    resultActions.add(result.action());
                }
            }
        }

        if (p.waits().equals(resultWaits)) {
            return;
        }

        try {
            boolean updated = processWaitManager.txResult(tx -> {
                boolean isWaiting = !resultWaits.isEmpty() && resumeEvents.isEmpty();
                boolean up = processWaitManager.setWait(tx, p.processKey(), resultWaits, isWaiting, p.version());
                if (up) {
                    resultActions.forEach(a -> a.execute(tx));
                }
                return up;
            });

            if (updated && !resumeEvents.isEmpty()) {
                resumeProcess(p.processKey(), resumeEvents);
            }
        } catch (Exception e) {
            log.info("processWaits ['{}'] -> error", p, e);
        }
    }

    @WithTimer
    ProcessWaitHandler.Result<AbstractWaitCondition> processWait(AbstractWaitCondition w, WaitingProcess p) {
        ProcessWaitHandler<AbstractWaitCondition> handler = processWaitHandlers.get(w.type());
        if (handler == null) {
            log.warn("processHandler ['{}'] -> handler '{}' not found", p.processKey(), w.type());
            return null;
        }

        try {
            return handler.process(p.processKey(), w);
        } catch (Exception e) {
            log.info("processHandler ['{}', '{}'] -> error", w.type(), p, e);
            return ProcessWaitHandler.Result.of(w);
        }
    }

    @WithTimer
    void resumeProcess(ProcessKey key, Set<String> events) {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(key, events, null);
        } catch (IOException e) {
            throw new RuntimeException("Error creating a payload", e);
        }

        processManager.resume(payload);
        log.info("resumeProcess ['{}', '{}'] -> done", key, events);
    }

    @Value.Immutable
    interface WaitingProcess {

        ProcessKey processKey();

        long id();

        List<AbstractWaitCondition> waits();

        long version();

        static ImmutableWaitingProcess.Builder builder() {
            return ImmutableWaitingProcess.builder();
        }
    }

    private static class WatchdogDao extends AbstractDao {

        private static final TypeReference<List<AbstractWaitCondition>> WAIT_LIST = new TypeReference<List<AbstractWaitCondition>>() {
        };

        private final ConcordObjectMapper objectMapper;

        private WatchdogDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);

            this.objectMapper = objectMapper;
        }

        @WithTimer
        public List<WaitingProcess> nextWaitItems(Long lastId, int pollLimit) {
            return txResult(tx -> {
                ProcessWaitConditions w = PROCESS_WAIT_CONDITIONS.as("w");

                SelectConditionStep<Record5<UUID, OffsetDateTime, Long, JSONB, Long>> s = tx.select(
                                w.INSTANCE_ID,
                                w.INSTANCE_CREATED_AT,
                                w.ID_SEQ,
                                w.WAIT_CONDITIONS,
                                w.VERSION)
                        .from(w)
                        .where(w.IS_WAITING.eq(true));

                if (lastId != null) {
                    s.and(w.ID_SEQ.greaterThan(lastId));
                }

                return s.orderBy(w.ID_SEQ)
                        .limit(pollLimit)
                        .fetch(r -> WaitingProcess.builder()
                                .processKey(new ProcessKey(r.value1(), r.value2()))
                                .id(r.value3())
                                .waits(objectMapper.fromJSONB(r.value4(), WAIT_LIST))
                                .version(r.value5())
                                .build());
            });
        }
    }
}
