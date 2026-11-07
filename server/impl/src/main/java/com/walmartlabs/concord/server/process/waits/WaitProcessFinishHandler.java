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
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.cfg.ProcessWaitWatchdogConfiguration;
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.metrics.ProcessQueueMetrics;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import org.jooq.Configuration;
import org.jooq.impl.EnumConverter;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.tables.ProcessQueue.PROCESS_QUEUE;
import static com.walmartlabs.concord.server.process.waits.ProcessCompletionCondition.CompleteCondition;
import static org.jooq.impl.DSL.any;

/**
 * Handles the processes that are waiting for other processes to finish.
 */
@Singleton
public class WaitProcessFinishHandler implements ProcessWaitHandler<ProcessCompletionCondition> {

    private final Dao dao;
    private final ProcessQueueMetrics metrics;

    private final int processLimitForStatusQuery;

    private final ProcessQueueManager processQueueManager;

    private final Histogram waitProcessesHistogram;


    @Inject
    public WaitProcessFinishHandler(Dao dao,
                                    ProcessWaitWatchdogConfiguration cfg,
                                    ProcessQueueManager processQueueManager,
                                    MetricRegistry metricRegistry,
                                    ProcessQueueMetrics metrics) {
        this.dao = dao;
        this.metrics = metrics;
        this.processLimitForStatusQuery = cfg.getProcessLimitForStatusQuery();
        this.processQueueManager = processQueueManager;
        this.waitProcessesHistogram = metricRegistry.histogram("wait-process-finish-histogram");
    }

    @Override
    public WaitType getType() {
        return WaitType.PROCESS_COMPLETION;
    }

    @Override
    @WithTimer
    public List<Result<ProcessCompletionCondition>> processBatch(List<WaitConditionItem<ProcessCompletionCondition>> items) {
        Set<UUID> allProcesses = items.stream()
                .flatMap(i -> i.waitCondition().processes().stream())
                .collect(Collectors.toSet());

        waitProcessesHistogram.update(allProcesses.size());

        List<WaitConditionItem<ProcessCompletionCondition>> modifiableItems = new ArrayList<>();
        for (WaitConditionItem<ProcessCompletionCondition> item : items) {
            modifiableItems.add(toModifiable(item));
        }

        // Identity keys remain stable while the modifiable conditions shrink across query batches.
        Map<WaitConditionItem<ProcessCompletionCondition>, OffsetDateTime> finishedAt = new IdentityHashMap<>();
        List<Set<UUID>> processesBatch = split(allProcesses, processLimitForStatusQuery);
        List<Result<ProcessCompletionCondition>> results = new ArrayList<>();
        for (Set<UUID> processes : processesBatch) {
            Map<UUID, Status> statuses = dao.findStatuses(processes);

            for (var it = modifiableItems.iterator(); it.hasNext(); ) {
                WaitConditionItem<ProcessCompletionCondition> item = it.next();

                OffsetDateTime resolvedAt = filterFinished(item.waitCondition(), statuses);
                if (resolvedAt != null) {
                    finishedAt.merge(item, resolvedAt, (a, b) -> a.isAfter(b) ? a : b);
                }
                if (item.waitCondition().processes().isEmpty()) {
                    OffsetDateTime latestFinish = finishedAt.remove(item);
                    // Missing/deleted blockers have no timestamp; don't invent a duration for them.
                    if (latestFinish != null) {
                        metrics.finishDelay.update(ProcessQueueMetrics.ageMillis(latestFinish, OffsetDateTime.now()));
                    }
                    results.add(toResult(item));
                    it.remove();
                }
            }

            if (modifiableItems.isEmpty()) {
                break;
            }
        }

        for (WaitConditionItem<ProcessCompletionCondition> item : modifiableItems) {
            results.add(toResult(item));
        }

        return results;
    }

    private Result<ProcessCompletionCondition> toResult(WaitConditionItem<ProcessCompletionCondition> item) {
        Set<UUID> newAwaitProcesses = item.waitCondition().processes();
        if (newAwaitProcesses.isEmpty()) {
            if (item.waitCondition().resumeEvent() != null) {
                return Result.resume(item, item.waitCondition().resumeEvent());
            } else {
                return Result.action(item, tx -> {
                    if (processQueueManager.updateExpectedStatus(tx, item.processKey(), ProcessStatus.WAITING, ProcessStatus.ENQUEUED)) {
                        metrics.enqueued.get(ProcessQueueMetrics.EnqueuedSource.WAIT_ACTION).inc();
                    }
                });
            }
        }

        return Result.of(item.processKey(), item.waitConditionId(),
                ProcessCompletionCondition.builder().from(item.waitCondition())
                        .processes(newAwaitProcesses)
                        .build());
    }

    private static WaitConditionItem<ProcessCompletionCondition> toModifiable(WaitConditionItem<ProcessCompletionCondition> item) {
        return WaitConditionItem.of(item.processKey(), item.waitConditionId(), ModifiableProcessCompletionCondition.create().from(item.waitCondition()));
    }

    private static List<Set<UUID>> split(Set<UUID> processes, int maxProcessesItems) {
        List<Set<UUID>> subsets = new ArrayList<>();
        Set<UUID> currentSet = new HashSet<>();

        for (UUID element : processes) {
            currentSet.add(element);
            if (currentSet.size() >= maxProcessesItems) {
                subsets.add(new HashSet<>(currentSet));
                currentSet.clear();
            }
        }

        if (!currentSet.isEmpty()) {
            subsets.add(currentSet);
        }

        return subsets;
    }

    private static OffsetDateTime filterFinished(ProcessCompletionCondition condition, Map<UUID, Status> statuses) {
        if (condition.processes() == null || condition.processes().isEmpty()) {
            return null;
        }

        OffsetDateTime latest = null;
        Iterator<UUID> it = condition.processes().iterator();
        while (it.hasNext()) {
            UUID proc = it.next();
            if (statuses.containsKey(proc)) {
                Status status = statuses.get(proc);
                if (status == null || condition.finalStatuses().contains(status.status())) {
                    if (status != null && status.lastUpdatedAt() != null
                            && (latest == null || status.lastUpdatedAt().isAfter(latest))) {
                        latest = status.lastUpdatedAt();
                    }
                    if (condition.completeCondition() == CompleteCondition.ALL) {
                        it.remove();
                    } else if (condition.completeCondition() == CompleteCondition.ONE_OF) {
                        condition.processes().clear();
                        return latest;
                    } else {
                        throw new RuntimeException("Unknown wait complete condition: " + condition.completeCondition());
                    }
                }
            }
        }
        return latest;
    }

    record Status(ProcessStatus status, OffsetDateTime lastUpdatedAt) {
    }

    public static class Dao extends AbstractDao {

        @Inject
        private Dao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public Map<UUID, Status> findStatuses(Set<UUID> processes) {
            Map<UUID, Status> result = txResult(tx -> {
                ProcessQueue q = PROCESS_QUEUE.as("q");
                return tx.select(q.INSTANCE_ID, q.CURRENT_STATUS, q.LAST_UPDATED_AT)
                        .from(q)
                        .where(q.INSTANCE_ID.equal(any(processes.toArray(UUID[]::new))))
                        .fetchMap(q.INSTANCE_ID, r -> new Status(r.get(q.CURRENT_STATUS, new EnumConverter<>(String.class, ProcessStatus.class)), r.get(q.LAST_UPDATED_AT)));
            });
            for (UUID key : processes) {
                result.putIfAbsent(key, null);
            }
            return result;
        }
    }
}
