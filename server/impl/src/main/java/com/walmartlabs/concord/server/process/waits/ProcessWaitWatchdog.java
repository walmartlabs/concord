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
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
import static com.walmartlabs.concord.server.process.waits.ProcessWaitHandler.WaitConditionItem;
import static com.walmartlabs.concord.server.process.waits.ProcessWaitHandler.Result;
import static com.walmartlabs.concord.server.process.waits.ProcessWaitHandler.Action;

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

            processWaits(processes);

            lastId = processes.get(processes.size() - 1).id();

            if (processes.size() < cfg.getPollLimit()) {
                return;
            }
        }
    }

    @WithTimer
    void processWaits(List<WaitingProcess> processes) {
        Map<WaitType, List<WaitConditionItem<AbstractWaitCondition>>> batches = toBatches(processes);

        Map<UUID, List<Result<AbstractWaitCondition>>> results = new HashMap<>();
        for (var e : batches.entrySet()) {
            List<Result<AbstractWaitCondition>> batchResult = processBatch(e.getKey(), e.getValue());
            for (var r : batchResult) {
                var resultsForProcess = results.computeIfAbsent(r.processKey().getInstanceId(), k -> new ArrayList<>());
                resultsForProcess.add(r);
            }
        }

        for (WaitingProcess p : processes) {
            List<Result<AbstractWaitCondition>> resultForProcess = results.get(p.processKey().getInstanceId());
            if (resultForProcess == null) {
                continue;
            }

            Set<String> resumeEvents = resultForProcess.stream()
                    .map(Result::resumeEvent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<Action> resultActions = resultForProcess.stream()
                    .map(Result::action)
                    .filter(Objects::nonNull)
                    .toList();
            List<AbstractWaitCondition> resultWaits = buildWaitConditions(p.waits(), resultForProcess);

            if (p.waits().equals(resultWaits)) {
                continue;
            }

            try {
                boolean updated = processWaitManager.txResult(tx -> {
                    // TODO: better way
                    // Right now, we have only one result action, and it moves the process to enqueued status. 
                    // If the process moves to enqueued, it means it's no longer waiting for anything, so isWaiting should be false
                    boolean isWaiting = !resultWaits.isEmpty() && resumeEvents.isEmpty() && resultActions.isEmpty();
                    boolean up = processWaitManager.setWait(tx, p.processKey(), resultWaits, isWaiting, p.version());
                    if (up) {
                        resultActions.forEach(a -> a.execute(tx));
                    }
                    return up;
                });

                if (updated && !resumeEvents.isEmpty()) {
                    log.info("processWaits ['{}', '{}', {}] -> resume", p.processKey(), resultWaits, p.version());
                    resumeProcess(p.processKey(), resumeEvents);
                }
            } catch (Exception e) {
                log.info("processWaits ['{}'] -> error", p, e);
            }
        }
    }

    @WithTimer
    List<Result<AbstractWaitCondition>> processBatch(WaitType type, List<WaitConditionItem<AbstractWaitCondition>> waitConditions) {
        ProcessWaitHandler<AbstractWaitCondition> handler = processWaitHandlers.get(type);
        if (handler == null) {
            log.warn("processBatch ['{}'] -> handler not found", type);
            return waitConditions.stream()
                    .map(w -> Result.of(w.processKey(), w.waitConditionId(), null))
                    .collect(Collectors.toList());
        }

        try {
            return handler.processBatch(waitConditions);
        } catch (Exception e) {
            log.info("processHandler ['{}'] -> error",type, e);
            return List.of();
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

    private static Map<WaitType, List<WaitConditionItem<AbstractWaitCondition>>> toBatches(List<WaitingProcess> processes) {
        Map<WaitType, List<WaitConditionItem<AbstractWaitCondition>>> result = new HashMap<>();

        for (WaitingProcess p : processes) {
            boolean hasExclusive = p.waits().stream().anyMatch(AbstractWaitCondition::exclusive);
            List<AbstractWaitCondition> waits = p.waits();
            for (int i = 0; i < waits.size(); i++) {
                AbstractWaitCondition w = waits.get(i);
                if (!hasExclusive || w.exclusive()) {
                    var r = result.computeIfAbsent(w.type(), k -> new ArrayList<>());
                    r.add(WaitConditionItem.of(p.processKey(), i, w));
                }
            }
        }
        return result;
    }

    private static List<AbstractWaitCondition> buildWaitConditions(List<AbstractWaitCondition> originalWaits,
                                                                   List<Result<AbstractWaitCondition>> newWaits) {

        List<AbstractWaitCondition> result = new ArrayList<>(originalWaits);
        for (Result<AbstractWaitCondition> w : newWaits) {
            result.set(w.waitConditionId(), w.waitCondition());
        }
        return result.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
