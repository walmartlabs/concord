package com.walmartlabs.concord.runtime.v25.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.LayoutBase;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v25.runner.task.TaskRuntime;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskContextTest {

    @Test
    void recordsCompleteTaskEventContext() {
        var invocation = new TaskRuntime.Invocation("test", "execute", List.of(Map.of()), List.of(),
                new TaskRuntime.StepContext("flows.default[0]", "process:1", "concord.yml", 7, 11,
                        Map.of("meta", Map.of("segmentName", "task")), null));

        var event = RunnerCallback.taskEvent(invocation);

        assertEquals("flows.default[0]", event.get("processDefinitionId"));
        assertEquals("concord.yml", event.get("fileName"));
        assertEquals(7, event.get("line"));
        assertEquals(11, event.get("column"));
        assertEquals(RunnerCallback.correlationId("process:1", invocation.step().metadata()),
                event.get("correlationId"));
    }

    @Test
    void restoresSegmentRoutingBetweenSequentialWorkerTasks() throws Exception {
        V25LogEncoder.segmented(true);
        V25LogEncoder.clearSegment();
        var encoder = new V25LogEncoder();
        var layout = new LayoutBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override
            public String doLayout(ch.qos.logback.classic.spi.ILoggingEvent event) {
                return event.getFormattedMessage();
            }
        };
        layout.start();
        encoder.setLayout(layout);
        encoder.start();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var first = worker.submit(() -> encodeSegment(encoder, 41L, "first")).get();
            var second = worker.submit(() -> encodeSegment(encoder, 42L, "second")).get();
            var after = worker.submit(() -> new String(encoder.encode(event("after")), StandardCharsets.UTF_8)).get();
            var firstFinal = new String(V25LogEncoder.status(41L, LogSegmentStatus.OK), StandardCharsets.UTF_8);
            var secondFinal = new String(V25LogEncoder.status(42L, LogSegmentStatus.ERROR), StandardCharsets.UTF_8);

            assertEquals(41L, first);
            assertEquals(42L, second);
            assertEquals(0L, segment(after));
            assertEquals(LogSegmentStatus.OK.id(), field(firstFinal, 3));
            assertEquals(LogSegmentStatus.ERROR.id(), field(secondFinal, 3));
        } finally {
            encoder.stop();
            V25LogEncoder.segmented(false);
            V25LogEncoder.clearSegment();
        }
    }

    private static long encodeSegment(V25LogEncoder encoder, long id, String message) {
        try (var ignored = V25LogEncoder.scope(id)) {
            return segment(new String(encoder.encode(event(message)), StandardCharsets.UTF_8));
        }
    }

    private static LoggingEvent event(String message) {
        var result = new LoggingEvent();
        result.setLevel(Level.INFO);
        result.setMessage(message);
        return result;
    }

    private static long segment(String value) {
        return Long.parseLong(value.split("\\|", 4)[2]);
    }

    private static long field(String value, int index) {
        return Long.parseLong(value.split("\\|")[index]);
    }
}
