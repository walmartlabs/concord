package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoopWithItemsTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    @Test
    public void emptyOuterLoopSkipsAllCompilerBodies() throws Exception {
        var log = run("loopWithItems/nested", List.of());
        assertEquals(0, Pattern.compile("(?:TASK|FLOW|SCRIPT)_(?:SERIAL|PARALLEL)_BODY_[ab]").matcher(log).results().count(), log);
        assertEquals(1, occurrences(log, "NESTED_DONE"), log);
        assertEquals(0, segmentCount(LogSegmentKind.ITERATION));
    }

    @Test
    public void outerLoopRepeatsEveryLegacyItemInSerialAndParallelModes() throws Exception {
        var log = run("loopWithItems/nested", List.of("x", "y"));
        for (var type : List.of("TASK", "FLOW", "SCRIPT")) {
            for (var mode : List.of("SERIAL", "PARALLEL")) {
                for (var item : List.of("a", "b")) {
                    assertEquals(2, occurrences(log, type + "_" + mode + "_BODY_" + item), log);
                }
            }
        }
        assertEquals(12, segmentCount(LogSegmentKind.ITERATION), "only outer loop iterations get iteration segments");
        assertEquals(0, segmentCount(LogSegmentKind.ATTEMPT), "legacy withItems must not gain attempt segments");
    }

    @Test
    public void retryIsPerLegacyItemAndErrorHandlesTheWholeOuterLoop() throws Exception {
        var log = run("loopWithItems/retryAndError", List.of("x", "y"));
        assertEquals(1, occurrences(log, "ConditionallyFailTask: ok"), log);
        assertEquals(2, occurrences(log, "ConditionallyFailTask: fail"), log);
        assertEquals(1, occurrences(log, "FLOW_ATTEMPT_a"), log);
        assertEquals(1, occurrences(log, "SCRIPT_ATTEMPT_a"), log);
        assertEquals(2, occurrences(log, "FLOW_ATTEMPT_b"), log);
        assertEquals(2, occurrences(log, "SCRIPT_ATTEMPT_b"), log);
        for (var type : List.of("TASK", "FLOW", "SCRIPT")) {
            assertEquals(1, occurrences(log, type + "_HANDLED"), log);
        }
        assertEquals(1, occurrences(log, "ERRORS_DONE"), log);
        assertEquals(3, segmentCount(LogSegmentKind.ITERATION));
        assertEquals(0, segmentCount(LogSegmentKind.ATTEMPT));
    }

    private static String run(String resource, List<String> outerItems) throws Exception {
        runtime.deploy(resource);
        runtime.save(ProcessConfiguration.builder().putArguments("outerItems", outerItems).build());
        return new String(runtime.run(RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder().segmentedLogs(true).build())
                .build()), StandardCharsets.UTF_8);
    }

    private static long occurrences(String log, String marker) {
        return Pattern.compile(Pattern.quote(marker)).matcher(log).results().count();
    }

    private static long segmentCount(LogSegmentKind kind) {
        var client = runtime.testLoggingClient();
        return client.getSegmentIds().stream().filter(id -> client.getSegmentAttributes(id).kind() == kind).count();
    }
}
