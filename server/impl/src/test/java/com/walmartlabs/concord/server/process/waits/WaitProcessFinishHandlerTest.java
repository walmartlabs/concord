package com.walmartlabs.concord.server.process.waits;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.server.metrics.ProcessQueueMetrics;
import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.server.cfg.ProcessWaitWatchdogConfiguration;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.process.waits.ProcessWaitHandler.WaitConditionItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anySet;
import static com.walmartlabs.concord.server.process.waits.ProcessWaitHandler.*;
import static org.mockito.Mockito.*;

public class WaitProcessFinishHandlerTest {

    private WaitProcessFinishHandler handler;
    private WaitProcessFinishHandler.Dao dao;

    @BeforeEach
    public void setUp() {
        var cfg = mock(ProcessWaitWatchdogConfiguration.class);
        when(cfg.getProcessLimitForStatusQuery()).thenReturn(2);

        dao = mock(WaitProcessFinishHandler.Dao.class);
        handler = new WaitProcessFinishHandler(dao, cfg, null, new MetricRegistry(), new ProcessQueueMetrics(new MetricRegistry()));
    }

    @Test
    public void testEmptyInput() {
        List<WaitConditionItem<ProcessCompletionCondition>> items = new ArrayList<>();
        List<Result<ProcessCompletionCondition>> results = handler.processBatch(items);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testAllProcessesFinished() {
        // ---
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        List<WaitConditionItem<ProcessCompletionCondition>> items = new ArrayList<>();
        items.add(WaitConditionItem.of(ProcessKey.random(), 0, ProcessCompletionCondition.builder().resumeEvent("test-resume").addProcesses(p1, p2).build()));
        items.add(WaitConditionItem.of(ProcessKey.random(), 1, ProcessCompletionCondition.builder().addProcesses(p1, p2, p3).build()));

        when(dao.findStatuses(anySet())).thenAnswer(
                (Answer<Map<UUID, WaitProcessFinishHandler.Status>>) invocation -> {
                    Set<UUID> processes = invocation.getArgument(0);
                    return processes.stream()
                            .collect(Collectors.toMap(
                                    p -> p,
                                    p -> new WaitProcessFinishHandler.Status(ProcessStatus.FINISHED, java.time.OffsetDateTime.now())
                            ));
                }
        );

        List<Result<ProcessCompletionCondition>> results = handler.processBatch(items);

        assertEquals(items.size(), results.size(), "All items should be processed");
        assertNull(results.get(0).waitCondition());
        assertNull(results.get(1).waitCondition());

        verify(dao, times(2)).findStatuses(anySet());
    }

    @Test
    public void testNotALLProcessesFinished() {
        // ---
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        List<WaitConditionItem<ProcessCompletionCondition>> items = new ArrayList<>();
        items.add(WaitConditionItem.of(ProcessKey.random(), 0, ProcessCompletionCondition.builder().resumeEvent("test-resume").addProcesses(p1, p2).build()));
        items.add(WaitConditionItem.of(ProcessKey.random(), 1, ProcessCompletionCondition.builder().addProcesses(p1, p2, p3).resumeEvent("my-event").exclusive(true).addFinalStatuses(ProcessStatus.FINISHED).build()));

        when(dao.findStatuses(anySet())).thenAnswer(
                (Answer<Map<UUID, WaitProcessFinishHandler.Status>>) invocation -> {
                    Set<UUID> processes = invocation.getArgument(0);
                    return processes.stream()
                            .collect(Collectors.toMap(
                                    p -> p,
                                    p -> new WaitProcessFinishHandler.Status(p == p3 ? ProcessStatus.RUNNING : ProcessStatus.FINISHED, java.time.OffsetDateTime.now())
                            ));
                }
        );

        List<Result<ProcessCompletionCondition>> results = handler.processBatch(items);

        assertEquals(items.size(), results.size(), "All items should be processed");

        assertNull(results.get(0).waitCondition());

        assertNotNull(results.get(1).waitCondition());
        assertEquals(Set.of(p3), results.get(1).waitCondition().processes());
        assertEquals("my-event", results.get(1).waitCondition().resumeEvent());
        assertTrue(results.get(1).waitCondition().exclusive());
        assertEquals(Set.of(ProcessStatus.FINISHED), results.get(1).waitCondition().finalStatuses());

        verify(dao, times(2)).findStatuses(anySet());
    }
    @Test
    void recordsLatestTimestampAcrossQueryBatchesAndCountsWaitAction() {
        var registry = new MetricRegistry();
        var cfg = mock(ProcessWaitWatchdogConfiguration.class);
        when(cfg.getProcessLimitForStatusQuery()).thenReturn(1);
        var manager = mock(com.walmartlabs.concord.server.process.queue.ProcessQueueManager.class);
        var metrics = new ProcessQueueMetrics(registry);
        var subject = new WaitProcessFinishHandler(dao, cfg, manager, registry, metrics);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var latest = java.time.OffsetDateTime.now().minusSeconds(2);
        when(dao.findStatuses(anySet())).thenAnswer(invocation -> {
            Set<UUID> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(id -> id, id -> new WaitProcessFinishHandler.Status(
                    ProcessStatus.FINISHED, id.equals(first) ? latest : latest.minusSeconds(100))));
        });
        var key = ProcessKey.random();
        var results = subject.processBatch(List.of(WaitConditionItem.of(key, 0,
                ProcessCompletionCondition.builder().addProcesses(first, second).build())));
        var histogram = registry.histogram("process-wait-finish-delay-ms");
        assertEquals(1, histogram.getCount());
        assertTrue(histogram.getSnapshot().getMin() >= 2000);
        assertTrue(histogram.getSnapshot().getMax() < 10000);
        var tx = mock(org.jooq.DSLContext.class);
        when(manager.updateExpectedStatus(tx, key, ProcessStatus.WAITING, ProcessStatus.ENQUEUED)).thenReturn(false, true);
        results.get(0).action().execute(tx);
        assertEquals(0, metrics.enqueued.get(ProcessQueueMetrics.EnqueuedSource.WAIT_ACTION).getCount());
        results.get(0).action().execute(tx);
        assertEquals(1, metrics.enqueued.get(ProcessQueueMetrics.EnqueuedSource.WAIT_ACTION).getCount());
    }

    @Test
    void missingBlockersStillResolveWithoutAnInventedTimestamp() {
        var registry = new MetricRegistry();
        var cfg = mock(ProcessWaitWatchdogConfiguration.class);
        when(cfg.getProcessLimitForStatusQuery()).thenReturn(2);
        var subject = new WaitProcessFinishHandler(dao, cfg, null, registry, new ProcessQueueMetrics(registry));
        var id = UUID.randomUUID();
        Map<UUID, WaitProcessFinishHandler.Status> statuses = new HashMap<>();
        statuses.put(id, null);
        when(dao.findStatuses(anySet())).thenReturn(statuses);
        var result = subject.processBatch(List.of(WaitConditionItem.of(ProcessKey.random(), 0,
                ProcessCompletionCondition.builder().addProcesses(id).resumeEvent("resume").build()))).get(0);
        assertEquals("resume", result.resumeEvent());
        assertEquals(0, registry.histogram("process-wait-finish-delay-ms").getCount());
    }

    @Test
    void oneOfResolvesAndClampsFutureCompletionTimestamp() {
        var registry = new MetricRegistry();
        var cfg = mock(ProcessWaitWatchdogConfiguration.class);
        when(cfg.getProcessLimitForStatusQuery()).thenReturn(2);
        var subject = new WaitProcessFinishHandler(dao, cfg, null, registry, new ProcessQueueMetrics(registry));
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(dao.findStatuses(anySet())).thenReturn(Map.of(
                first, new WaitProcessFinishHandler.Status(ProcessStatus.RUNNING, java.time.OffsetDateTime.now()),
                second, new WaitProcessFinishHandler.Status(ProcessStatus.FINISHED, java.time.OffsetDateTime.now().plusHours(1))));
        var result = subject.processBatch(List.of(WaitConditionItem.of(ProcessKey.random(), 0,
                ProcessCompletionCondition.builder().addProcesses(first, second).resumeEvent("resume")
                        .completeCondition(ProcessCompletionCondition.CompleteCondition.ONE_OF).build()))).get(0);
        assertNull(result.waitCondition());
        assertEquals(1, registry.histogram("process-wait-finish-delay-ms").getCount());
        assertEquals(0, registry.histogram("process-wait-finish-delay-ms").getSnapshot().getMax());
    }

}
