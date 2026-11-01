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

import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentAttributes;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogSegmentKind;
import com.walmartlabs.concord.runtime.v2.runner.tasks.Tasks;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tree of log segments: kinds, parents, names and results.
 */
public class LogTreeTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    // Status markers have either no payload or a 13-digit timestamp payload.
    private static final Pattern STATUS = Pattern.compile("\\|(?:0|13)\\|(\\d+)\\|([0-3])\\|\\d+\\|\\d+\\|(?:\\d{13})?");

    @Test
    public void complex() throws Exception {
        assertEquals("""
                LOOP deploy ${item} ERROR
                  ATTEMPT#0 deploy a OK
                    STEP work a OK
                  ATTEMPT#0 deploy b ERROR
                    STEP work b ERROR
                  ATTEMPT#1 deploy b ERROR
                    STEP work b ERROR
                  ERROR error OK
                    STEP task: log OK
                    STEP cleanup OK
                STEP block ERROR
                  PARALLEL parallel ERROR
                    STEP branch A ERROR
                    STEP branch B OK
                  ERROR error OK
                    STEP task: log OK
                """, run("logTree/complex"));
    }

    /**
     * {@code return} in the {@code error} block exits the handler, the step remains failed.
     */
    @Test
    public void handlerReturn() throws Exception {
        assertEquals("""
                STEP faulty ERROR
                  ERROR error OK
                    STEP task: log OK
                STEP after OK
                """, run("logTree/handlerReturn"));
    }

    @Test
    public void parallelEventBelongsToItsSegment() throws Exception {
        run("logSegments/nestedSegments");
        var client = runtime.testLoggingClient();
        var parallelId = client.getSegmentIds().stream()
                .filter(id -> client.getSegmentAttributes(id).kind() == LogSegmentKind.PARALLEL)
                .findFirst().orElseThrow();
        var eventSegmentIds = runtime.testEventReportingService().elementEvents().stream()
                .filter(e -> "Parallel block".equals(e.get("description")))
                .map(e -> e.get("logSegmentId"))
                .toList();
        assertEquals(List.of(parallelId), eventSegmentIds);
    }

    @Test
    public void nestedSegments() throws Exception {
        assertEquals("""
                STEP outer OK
                  STEP inner task OK
                  PARALLEL parallel OK
                    STEP branch A OK
                    STEP branch B OK
                      STEP leaf task OK
                STEP top level OK
                """, run("logSegments/nestedSegments"));
    }

    /**
     * A repeated step retains its name template; iterations or attempts get the evaluated name.
     */
    @Test
    public void loopsAndRetries() throws Exception {
        assertEquals("""
                RETRY retried OK
                  ATTEMPT#0 retried ERROR
                  ATTEMPT#1 retried OK
                LOOP serial ${item} OK
                  ITERATION#0 serial a OK
                  ITERATION#1 serial b OK
                LOOP parallel ${item} OK
                  ITERATION#0 parallel a OK
                  ITERATION#1 parallel b OK
                LOOP looped retry ${item} OK
                  ATTEMPT#0 looped retry x ERROR
                  ATTEMPT#1 looped retry x OK
                  ATTEMPT#0 looped retry y ERROR
                  ATTEMPT#1 looped retry y OK
                STEP caught ERROR
                  ERROR error OK
                    STEP handler OK
                RETRY outer OK
                  ATTEMPT#0 outer OK
                    PARALLEL parallel OK
                      STEP branch OK
                """, run("logSegments/segmentAttributes"));

        TestRuntimeV2.TestLoggingClient client = runtime.testLoggingClient();
        assertEquals(List.of("0:0", "0:1", "1:0", "1:1"), client.getSegmentIds().stream()
                .filter(id -> client.getSegmentName(id).startsWith("looped retry "))
                .map(client::getSegmentAttributes)
                .filter(a -> a.kind() == LogSegmentKind.ATTEMPT)
                .map(a -> a.iterationIndex() + ":" + a.attemptIndex())
                .toList());

        // events are linked to the segment of the particular run of the step
        assertEquals(List.of("ATTEMPT#0 retried", "ATTEMPT#1 retried",
                        "ATTEMPT#0 looped retry x", "ATTEMPT#1 looped retry x",
                        "ATTEMPT#0 looped retry y", "ATTEMPT#1 looped retry y"),
                eventSegments(e -> "conditionallyFailTask".equals(e.get("name"))));
        assertEquals(List.of("STEP caught"), eventSegments(e -> "faultyTask".equals(e.get("name"))));
        assertEquals(List.of("ATTEMPT#0 outer"), eventSegments(e -> "Flow call: inner".equals(e.get("description"))));
    }

    @Test
    public void errorHandlers() throws Exception {
        assertEquals("""
                LOOP block OK
                  ITERATION#0 iteration #1 OK
                    STEP loop failure a ERROR
                      ERROR error OK
                        STEP loop handler OK
                  ITERATION#1 iteration #2 OK
                    STEP loop failure b ERROR
                      ERROR error OK
                        STEP loop handler OK
                STEP withItems failure a ERROR
                  ERROR error OK
                    STEP withItems handler OK
                STEP withItems failure b ERROR
                  ERROR error OK
                    STEP withItems handler OK
                STEP failed before recovery ERROR
                  ERROR error OK
                    STEP guarded recovery OK
                    STEP Recovery nested flow OK
                      PARALLEL parallel OK
                        STEP Recovery parallel A OK
                        STEP Recovery parallel B OK
                STEP call: missingFlow ERROR
                  ERROR error OK
                    STEP missing flow handler OK
                """, run("logSegments/errorHandlerAttributes"));
    }

    /**
     * The attempt is closed when the retry starts, the next failure in the block belongs to the block.
     */
    @Test
    public void errorAfterHandledRetry() throws Exception {
        Tasks.FailOnceTask.reset();
        assertEquals("""
                STEP outer OK
                  STEP block ERROR
                    RETRY retried OK
                      ATTEMPT#0 retried ERROR
                      ATTEMPT#1 retried OK
                    ERROR error OK
                      STEP handler OK
                """, run("logSegments/errorHandlerAfterHandledRetry"));
    }

    /**
     * A failed step without a segment of its own fails the enclosing segment.
     */
    @Test
    public void errorOfAStepWithoutASegment() throws Exception {
        assertEquals("""
                STEP outer OK
                  STEP block ERROR
                    ERROR error OK
                      STEP handler OK
                """, run("logSegments/errorHandlerOfUnnamedStep"));
    }

    @Test
    public void parallelInErrorHandler() throws Exception {
        assertEquals("""
                STEP failed ERROR
                  ERROR error OK
                    PARALLEL parallel OK
                      STEP branch A OK
                      STEP branch B OK
                """, run("logSegments/parallelInErrorHandler"));
    }

    /**
     * A failed handler fails its step, the enclosing handler recovers the enclosing step.
     */
    @Test
    public void rethrowingErrorHandler() throws Exception {
        assertEquals("""
                STEP outer ERROR
                  STEP failed ERROR
                    ERROR error ERROR
                      STEP inner handler OK
                      STEP task: throw ERROR
                  ERROR error OK
                    STEP outer handler OK
                """, run("logSegments/rethrowingErrorHandler"));
    }

    @Test
    public void retryReleasesTheError() throws Exception {
        Tasks.FailOnceTask.reset();
        assertEquals("""
                RETRY retried OK
                  ATTEMPT#0 retried ERROR
                  ATTEMPT#1 retried OK
                STEP task: stateReferencesErrorTask OK
                """, run("logSegments/retryReleasesTheError"));
        assertEquals(false, Tasks.StateReferencesErrorTask.result(), "the state references the handled error");
    }

    @Test
    public void suspendInErrorHandler() throws Exception {
        run("logSegments/suspendInErrorHandler");

        StateManager.saveResumeEvent(runtime.workDir(), "ev");
        assertEquals("""
                STEP failed ERROR
                  ERROR error OK
                    STEP task: suspendTask OK
                    STEP after resume OK
                """, renderTree(runtime.run(runnerCfg())));
    }

    /**
     * The deprecated {@code withItems} creates a segment for each item, no iteration segments.
     */
    @Test
    public void withItemsInLoop() throws Exception {
        assertEquals("""
                LOOP block OK
                  ITERATION#0 iteration #1 OK
                    STEP item p OK
                    STEP item q OK
                  ITERATION#1 iteration #2 OK
                    STEP item p OK
                    STEP item q OK
                STEP parallel x OK
                STEP parallel y OK
                """, run("logSegments/withItemsInLoop"));
    }

    @Test
    public void repeatedNamesRemainTemplatesBeforeAnyIterationStarts() throws Exception {
        run("logTree/nameTemplates");
        var client = runtime.testLoggingClient();
        for (var name : List.of("empty task ${item}", "empty call ${item}", "empty script ${item}",
                "empty block ${item}", "invalid loop ${item}")) {
            var id = client.getSegmentId(name);
            assertEquals(LogSegmentKind.LOOP, client.getSegmentAttributes(id).kind(), name);
            assertEquals(0, client.getSegmentIds().stream()
                    .filter(child -> Objects.equals(client.getSegmentParentId(child), id))
                    .filter(child -> client.getSegmentAttributes(child).kind() == LogSegmentKind.ITERATION)
                    .count(), name);
        }
    }

    @Test
    public void iterationIndexIsPrivateAndScopedToItsRetry() throws Exception {
        String tree = run("logTree/iterationScope");
        org.junit.jupiter.api.Assertions.assertFalse(tree.contains("check variables ERROR"), tree);
        var client = runtime.testLoggingClient();
        var attempts = client.getSegmentIds().stream()
                .filter(id -> client.getSegmentAttributes(id).kind() == LogSegmentKind.ATTEMPT)
                .toList();
        assertEquals(List.of("0:0", "1:0"), attempts.stream()
                .filter(id -> client.getSegmentName(id).startsWith("outer "))
                .map(client::getSegmentAttributes)
                .map(a -> a.iterationIndex() + ":" + a.attemptIndex()).sorted().toList());
        assertEquals(List.of("null:0", "null:0"), attempts.stream()
                .filter(id -> client.getSegmentName(id).equals("check variables"))
                .map(client::getSegmentAttributes)
                .map(a -> a.iterationIndex() + ":" + a.attemptIndex()).sorted().toList());
        assertEquals(List.of("0:0", "0:0", "0:1", "0:1", "1:0", "1:0", "1:1", "1:1"), attempts.stream()
                .filter(id -> client.getSegmentName(id).startsWith("nested "))
                .map(client::getSegmentAttributes)
                .map(a -> a.iterationIndex() + ":" + a.attemptIndex()).sorted().toList());
    }

    @Test
    public void explicitParentKindsSurviveSuspendInErrorHandler() throws Exception {
        runtime.deploy("logTree/parentKindsSuspend");
        runtime.save(ProcessConfiguration.builder().build());
        var log = new ByteArrayOutputStream();
        log.write(runtime.run(runnerCfg()));
        StateManager.saveResumeEvent(runtime.workDir(), "loop-handler");
        log.write(runtime.run(runnerCfg()));
        StateManager.saveResumeEvent(runtime.workDir(), "retry-handler");
        log.write(runtime.run(runnerCfg()));
        assertEquals("""
                LOOP loop ${item} ERROR
                  ATTEMPT#0 loop a ERROR
                  ATTEMPT#1 loop a ERROR
                  ERROR error OK
                    STEP task: suspendTask OK
                RETRY retried ERROR
                  ATTEMPT#0 retried ERROR
                  ATTEMPT#1 retried ERROR
                  ERROR error OK
                    STEP task: suspendTask OK
                STEP after handlers OK
                """, renderTree(log.toByteArray()));
    }

    private static String run(String resource) throws Exception {
        runtime.deploy(resource);
        runtime.save(ProcessConfiguration.builder().build());
        return renderTree(runtime.run(runnerCfg()));
    }

    private static RunnerConfiguration runnerCfg() {
        return RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();
    }

    /**
     * Segments of the ELEMENT events (pre and post of a task call are reported once).
     */
    private static List<String> eventSegments(Predicate<Map<String, Object>> filter) {
        TestRuntimeV2.TestLoggingClient client = runtime.testLoggingClient();
        return runtime.testEventReportingService().elementEvents().stream()
                .filter(filter)
                .map(e -> ((Number) e.get("logSegmentId")).longValue())
                .distinct()
                .map(id -> {
                    LogSegmentAttributes a = client.getSegmentAttributes(id);
                    return a.kind() + indexSuffix(a) + " " + client.getSegmentName(id);
                })
                .toList();
    }

    /**
     * One line per segment, children under their parent in creation order. Parallel siblings
     * are sorted by name: their creation order is not deterministic.
     */
    private static String renderTree(byte[] log) {
        Map<Long, LogSegmentStatus> statuses = new HashMap<>();
        Matcher m = STATUS.matcher(new String(log, StandardCharsets.UTF_8));
        while (m.find()) {
            statuses.put(Long.parseLong(m.group(1)), LogSegmentStatus.fromId(Integer.parseInt(m.group(2))));
        }

        TestRuntimeV2.TestLoggingClient client = runtime.testLoggingClient();
        StringBuilder sb = new StringBuilder();
        render(client, statuses, null, 0, sb);
        return sb.toString();
    }

    private static String indexSuffix(LogSegmentAttributes attributes) {
        Integer index = attributes.attemptIndex() != null ? attributes.attemptIndex() : attributes.iterationIndex();
        return index != null ? "#" + index : "";
    }

    private static void render(TestRuntimeV2.TestLoggingClient client, Map<Long, LogSegmentStatus> statuses,
                               Long parentId, int depth, StringBuilder sb) {

        List<Long> children = client.getSegmentIds().stream()
                .filter(id -> Objects.equals(client.getSegmentParentId(id), parentId))
                .toList();

        // segments of child threads take the place of the first of them
        long firstForked = children.stream()
                .filter(id -> client.getSegmentAttributes(id).threadId() != null)
                .findFirst()
                .orElse(Long.MAX_VALUE);

        children.stream()
                .sorted(Comparator
                        .comparing((Long id) -> client.getSegmentAttributes(id).threadId() != null ? firstForked : id)
                        .thenComparing(client::getSegmentName)
                        .thenComparing(id -> Objects.requireNonNullElse(client.getSegmentAttributes(id).attemptIndex(), 0)))
                .forEach(id -> {
                    LogSegmentAttributes a = client.getSegmentAttributes(id);
                    sb.append("  ".repeat(depth))
                            .append(a.kind())
                            .append(indexSuffix(a))
                            .append(' ').append(client.getSegmentName(id))
                            .append(' ').append(statuses.getOrDefault(id, LogSegmentStatus.RUNNING))
                            .append('\n');
                    render(client, statuses, id, depth + 1, sb);
                });
    }
}
