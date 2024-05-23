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
        handler = new WaitProcessFinishHandler(dao, cfg, null, new MetricRegistry());
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
                (Answer<Map<UUID, ProcessStatus>>) invocation -> {
                    Set<UUID> processes = invocation.getArgument(0);
                    return processes.stream()
                            .collect(Collectors.toMap(
                                    p -> p,
                                    p -> ProcessStatus.FINISHED
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
                (Answer<Map<UUID, ProcessStatus>>) invocation -> {
                    Set<UUID> processes = invocation.getArgument(0);
                    return processes.stream()
                            .collect(Collectors.toMap(
                                    p -> p,
                                    p -> p == p3 ? ProcessStatus.RUNNING : ProcessStatus.FINISHED
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
}
