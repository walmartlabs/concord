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
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.PROCESS_WAIT_CONDITIONS;
import static com.walmartlabs.concord.server.process.waits.ProcessWaitHandler.Result;
import static com.walmartlabs.concord.server.process.waits.ProcessWaitHandler.WaitConditionItem;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

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
            var processes = dao.nextWaitItems(lastId, cfg.getPollLimit());
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
        // split waiting processes by their WaitType
        // "exclusive" wait conditions are processed first
        var batches = toBatches(processes);

        // process each batch
        var results = batches.stream()
                .map(this::processBatch)
                .flatMap(Collection::stream)
                .collect(groupingBy(Result::processKey));

        for (WaitingProcess p : processes) {
            var resultForProcess = results.get(p.processKey());
            if (resultForProcess == null) {
                // no results for this process on this iteration
                continue;
            }

            var resultWaits = resultForProcess.stream()
                    .sorted(comparing(Result::waitConditionId))
                    .map(Result::waitCondition)
                    .toList();

            if (p.waits().equals(resultWaits)) {
                continue;
            }

            var resumeEvents = resultForProcess.stream()
                    .map(Result::resumeEvent)
                    .filter(Objects::nonNull)
                    .collect(toSet());

            try {
                boolean updated = processWaitManager.txResult(tx -> {
                    boolean isWaiting = !resultWaits.isEmpty() && resumeEvents.isEmpty();
                    boolean up = processWaitManager.setWait(tx, p.processKey(), resultWaits, isWaiting, p.version());
                    if (up) {
                        resultForProcess.stream()
                                .map(Result::action)
                                .filter(Objects::nonNull)
                                .forEach(a -> a.execute(tx));
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
    private List<Result<AbstractWaitCondition>> processBatch(Batch batch) {
        var type = batch.type();
        var waitConditions = batch.items();

        var handler = processWaitHandlers.get(type);
        if (handler == null) {
            log.warn("processBatch ['{}'] -> handler not found", type);
            return waitConditions.stream()
                    .map(w -> Result.of(w.processKey(), w.waitConditionId(), null))
                    .toList();
        }

        try {
            return handler.processBatch(waitConditions);
        } catch (Exception e) {
            log.info("processHandler ['{}'] -> error", type, e);
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

    private record Batch(WaitType type, List<WaitConditionItem<AbstractWaitCondition>> items) {
    }

    private static List<Batch> toBatches(List<WaitingProcess> processes) {
        var result = new HashMap<WaitType, List<WaitConditionItem<AbstractWaitCondition>>>();

        for (WaitingProcess p : processes) {
            boolean hasExclusive = p.waits().stream().anyMatch(AbstractWaitCondition::exclusive);
            var waits = p.waits();
            for (int i = 0; i < waits.size(); i++) {
                var waitCondition = waits.get(i);
                if (!hasExclusive || waitCondition.exclusive()) {
                    result.computeIfAbsent(waitCondition.type(), k -> new ArrayList<>())
                            .add(WaitConditionItem.of(p.processKey(), i, waitCondition));
                }
            }
        }

        return result.entrySet().stream()
                .map(e -> new Batch(e.getKey(), e.getValue()))
                .toList();
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
                var PWC = PROCESS_WAIT_CONDITIONS.as("w");
                var query = tx.select(
                                PWC.INSTANCE_ID,
                                PWC.INSTANCE_CREATED_AT,
                                PWC.ID_SEQ,
                                PWC.WAIT_CONDITIONS,
                                PWC.VERSION)
                        .from(PWC)
                        .where(PWC.IS_WAITING.eq(true));

                if (lastId != null) {
                    query.and(PWC.ID_SEQ.greaterThan(lastId));
                }

                return query.orderBy(PWC.ID_SEQ)
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
