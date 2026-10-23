package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.runner.tasks.ReentrantTaskExample;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2.assertLog;
import static com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2.assertMultiLineLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LogSegmentsTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    private static final ThreadLocal<byte[]> logWithoutSegmentsHolder = new ThreadLocal<>();

    @Test
    public void testSystemOutRedirectInScripts() throws Exception {
        deploy("systemOutRedirect");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] System.out in a script");
        assertSegmentStatusOk(log, 1);
        assertNoMoreSegments();
    }

    @Test
    public void unnamedExpressionRespectsLogLevel() throws Exception {
        deploy("logSegments/exprLogLevel");
        save(ProcessConfiguration.builder().build());
        byte[] log = runWithSegments();
        String text = new String(log, StandardCharsets.UTF_8);
        assertFalse(text.contains("system filtered info"));
        assertFalse(text.contains("parent filtered info"));
        assertSegmentLog(log, 0, "[WARN ] system filtered warn");
        assertSegmentLog(log, 0, "[INFO ] system after info");
        assertSegmentLog(log, 0, "[WARN ] system after warn");
        assertSegmentName(1, "Parent");
        assertSegmentLog(log, 1, "[WARN ] parent filtered warn");
        assertSegmentLog(log, 1, "[INFO ] parent after info");
        assertSegmentLog(log, 1, "[WARN ] parent after warn");
        assertSegmentStatusOk(log, 1);
        assertEquals(1, runtime.testLoggingClient().getSegmentIds().size(),
                "Unnamed expressions must not create their own segments");
        assertNoMoreSegments();
    }

    @Test
    public void nameEvaluationErrorBelongsToParentSegment() throws Exception {
        deploy("logSegments/nameEvaluationError");
        save(ProcessConfiguration.builder().build());

        assertThrows(Exception.class, this::runWithSegments);

        byte[] log = runtime.lastLog();
        assertSegmentName(1, "Parent");
        assertSegmentLogPattern(log, 1, null, "\\[ERROR\\].*missingNameVariable.*");
        assertSegmentStatusError(log, 1);
        assertEquals(1, runtime.testLoggingClient().getSegmentIds().size(),
                "A failed name evaluation must not create a child segment");
        assertFalse(Pattern.compile("\\|\\d+\\|0\\|\\d+\\|\\d+\\|\\d+\\|[^\\n]*missingNameVariable")
                .matcher(new String(log, StandardCharsets.UTF_8)).find(),
                "The name evaluation error must not be logged in the system segment");
    }

    @Test
    public void throwStepShouldContainErrorDescription() throws Exception {
        deploy("logSegments/throw");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        assertSegmentLog(runtime.lastLog(), 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. BOOM");
        assertSegmentStatusError(runtime.lastLog(), 1);
        assertNoMoreSegments();
    }

    @Test
    public void loopStepShouldLogErrorInProperLogSegment() throws Exception {
        deploy("logSegments/taskErrorWithLoop");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // the step, the parallel errors are logged into it
        assertSegmentStatusError(runtime.lastLog(), 1);

        // the iterations
        for (int segmentId = 2; segmentId <= 7; segmentId++) {
            assertSegmentMultilineLog(runtime.lastLog(), segmentId, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
            assertSegmentStatusError(runtime.lastLog(), segmentId);
        }
    }

    @Test
    public void taskTest() throws Exception {
        deploy("logSegments/task");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] Message");
        assertSegmentStatusOk(log, 1);
        assertNoMoreSegments();
    }

    @Test
    public void taskRetryTest() throws Exception {
        deploy("logSegments/taskWithRetry");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // the step
        assertSegmentLog(log, 1, "[WARN ] Last error: boom!. Waiting for 1000ms before retry (attempt #0)");
        assertSegmentStatusOk(log, 1);

        // first attempt
        assertSegmentLog(log, 2, "[INFO ] ConditionallyFailTask: fail");
        assertSegmentLog(log, 2, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
        assertSegmentStatusError(log, 2);

        // second
        assertSegmentLog(log, 3, "[INFO ] ConditionallyFailTask: ok");
        assertSegmentStatusOk(log, 3);

        assertNoMoreSegments();
    }

    @Test
    public void taskErrorWithReturn() throws Exception {
        deploy("logSegments/taskErrorWithReturn");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // the step, "return" exits the error block
        assertSegmentLog(log, 1, "[INFO ] will fail with error");
        assertSegmentLog(log, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
        assertSegmentStatusError(log, 1);

        // error block
        assertSegmentStatusOk(log, 2);
        assertSegmentLog(log, 3, "[INFO ] in error block");
        assertSegmentStatusOk(log, 3);

        assertNoMoreSegments();
    }

    @Test
    public void taskLoopSerialTest() throws Exception {
        deploy("logSegments/taskLoopSerial");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // the step
        assertSegmentStatusOk(log, 1);

        // the iterations
        assertSegmentLog(log, 2, "[INFO ] test 1");
        assertSegmentStatusOk(log, 2);

        assertSegmentLog(log, 3, "[INFO ] test 2");
        assertSegmentStatusOk(log, 3);

        assertSegmentLog(log, 4, "[INFO ] test 3");
        assertSegmentStatusOk(log, 4);

        assertNoMoreSegments();
    }

    @Test
    public void taskLoopParallelTest() throws Exception {
        deploy("logSegments/taskLoopParallel");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // the step
        assertSegmentStatusOk(log, 1);

        // the iterations
        for (int segmentId = 2; segmentId <= 4; segmentId++) {
            assertSegmentLog(log, segmentId, "[INFO ] test");
            assertSegmentStatusOk(log, segmentId);
        }

        assertNoMoreSegments();
    }

    @Test
    public void taskLoopWithErrorTest() throws Exception {
        deploy("logSegments/taskLoopWithError");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // the step
        assertSegmentStatusError(runtime.lastLog(), 1);

        // the iterations
        assertSegmentLog(runtime.lastLog(), 2, "[INFO ] ConditionallyFailTask: ok");
        assertSegmentStatusOk(runtime.lastLog(), 2);

        assertSegmentLog(runtime.lastLog(), 3, "[INFO ] ConditionallyFailTask: fail");
        assertSegmentLog(runtime.lastLog(), 3, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
        assertSegmentStatusError(runtime.lastLog(), 3);

        assertNoMoreSegments();
    }

    @Test
    public void taskOutInvalidTest() throws Exception {
        deploy("logSegments/taskOutInvalid");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // invalid task out definition -> log into task segment
        assertSegmentLog(runtime.lastLog(), 1, "[INFO ] test");
        assertSegmentLog(runtime.lastLog(), 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
        assertSegmentStatusError(runtime.lastLog(), 1);

        assertNoMoreSegments();
    }

    @Test
    public void taskLoopInvalidTest() throws Exception {
        deploy("logSegments/taskLoopInvalid");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // the step's segment is opened before the loop
        assertSegmentLog(runtime.lastLog(), 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
        assertSegmentStatusError(runtime.lastLog(), 1);

        assertNoMoreSegments();
    }

    @Test
    public void taskSuspendTest() throws Exception {
        deploy("logSegments/taskWithSuspend");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] will suspend with event: 'sleepingBeauty'");
        assertSegmentStatusSuspended(log, 1);

        assertNoMoreSegments();

        log = resumeWithSegments("sleepingBeauty");

        // running
        assertSegmentStatusRunning(log, 1);

        // ok
        assertSegmentStatusOk(log, 1);

        assertNoMoreSegments();
    }

    @Test
    public void taskReentrantTest() throws Exception {
        deploy("logSegments/taskWithReentrantSuspend");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] execute {action=boo}");
        assertSegmentStatusSuspended(log, 1);

        assertNoMoreSegments();

        log = resumeWithSegments(ReentrantTaskExample.EVENT_NAME);

        assertSegmentStatusRunning(log, 1);
        assertSegmentLogPattern(log, 1, 157, "\\[INFO \\] RESUME: .*");

        assertSegmentStatusOk(log, 1);

        assertNoMoreSegments();
    }

    @Test
    public void taskErrorTest() throws Exception {
        // task throws error
        deploy("logSegments/taskError");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // task error segment
        assertSegmentLog(runtime.lastLog(), 1, "[INFO ] will fail with error");
        assertSegmentLog(runtime.lastLog(), 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
        assertSegmentStatusError(runtime.lastLog(), 1);

        assertNoMoreSegments();
    }

    @Test
    public void taskErrorWithErrorTest() throws Exception {
        // task with error handler throws error
        deploy("logSegments/taskErrorWithError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // task error into task segment, the successful error block does not change its failure status
        assertSegmentLog(log, 1, "[INFO ] will fail with error");
        assertSegmentLog(log, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
        assertSegmentStatusError(log, 1);

        // error block
        assertSegmentStatusOk(log, 2);

        // error handler logs
        assertSegmentLog(log, 3, "[INFO ] in error block");
        assertSegmentStatusOk(log, 3);

        assertNoMoreSegments();
    }

    @Test
    public void taskWithInvalidName() throws Exception {
        deploy("logSegments/taskWithInvalidName");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        assertSystemSegment(runtime.lastLog(), "[ERROR] (concord.yaml): Error @ line: 4, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
        assertNoMoreSegments();
    }

    @Test
    public void taskUndefined() throws Exception {
        // undefined task call
        deploy("logSegments/taskUndefined");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // logs to the system segment
        assertSegmentLog(runtime.lastLog(), 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Task not found: 'undefinedTask'");
        assertSegmentStatusError(runtime.lastLog(), 1);

        assertNoMoreSegments();
    }

    @Test
    public void taskWithRetryInvalid() throws Exception {
        // task step with invalid retry expressions
        deploy("logSegments/taskWithRetryInvalid");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // the step's segment is opened before the retry
        assertSegmentLog(runtime.lastLog(), 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
        assertSegmentStatusError(runtime.lastLog(), 1);
        assertNoMoreSegments();
    }

    @Test
    public void checkpointInvalidTest() throws Exception {
        // checkpoint with invalid expression for checkpoint name
        deploy("logSegments/checkpointInvalid");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error into system segment
        assertSystemSegment(runtime.lastLog(), "[ERROR] (concord.yaml): Error @ line: 3, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
    }

    @Test
    public void exprWithoutNameTest() throws Exception {
        deploy("logSegments/expr");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // expr without name -> system segment
        assertSystemSegment(log, "[INFO ] simple");
        assertNoMoreSegments();
    }

    @Test
    public void exprWithNameTest() throws Exception {
        deploy("logSegments/exprWithName");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // expr without name -> system segment
        assertSegmentLog(log, 1, "[INFO ] simple");
        assertSegmentStatusOk(log, 1);

        assertNoMoreSegments();
    }

    @Test
    public void exprInvalidTest() throws Exception {
        deploy("logSegments/exprInvalid");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error into system segment
        assertSystemSegment(runtime.lastLog(), "[ERROR] (concord.yaml): Error @ line: 3, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
    }

    @Test
    public void scriptTest() throws Exception {
        deploy("logSegments/script");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] log message");
        assertSegmentStatusOk(log, 1);
        assertNoMoreSegments();
    }

    @Test
    public void scriptInvalidTest() throws Exception {
        deploy("logSegments/scriptInvalid");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        assertSegmentLog(runtime.lastLog(), 1, "[ERROR] (concord.yml): Error @ line: 3, col: 7. Unknown language 'js234'. Check process dependencies.");
        assertSegmentStatusError(runtime.lastLog(), 1);
        assertNoMoreSegments();
    }

    @Test
    public void scriptInvalidBodyTest() throws Exception {
        deploy("logSegments/scriptInvalidBody");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        assertSegmentLogPattern(runtime.lastLog(), 1, null, Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. TypeError: invokeMember (unknownMethod) on") + ".*");
        assertSegmentStatusError(runtime.lastLog(), 1);
    }

    @Test
    public void callTest() throws Exception {
        deploy("logSegments/call");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] in inner flow");
        assertSegmentStatusOk(log, 1);
        assertNoMoreSegments();
    }

    @Test
    public void callWithNameTest() throws Exception {
        deploy("logSegments/callWithName");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 2, "[INFO ] in inner flow");
        assertSegmentStatusOk(log, 2);

        // segment 1 without any logs (named call step)
        assertSegmentStatusOk(log, 1);
        assertNoMoreSegments();
    }

    @Test
    public void callWithNameLoopTest() throws Exception {
        deploy("logSegments/callWithNameLoop");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // (named call step)
        assertSegmentStatusOk(log, 1);

        // an iteration and the task in it
        for (int item = 1; item <= 3; item++) {
            int iteration = item * 2;
            assertSegmentStatusOk(log, iteration);
            assertSegmentLog(log, iteration + 1, "[INFO ] in inner flow, item: " + item);
            assertSegmentStatusOk(log, iteration + 1);
        }

        assertNoMoreSegments();
    }

    @Test
    public void callWithRetryTest() throws Exception {
        deploy("logSegments/callWithRetry");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // the call step
        assertSegmentLog(log, 1, "[WARN ] Last error: FAIL. Waiting for 1000ms before retry (attempt #0)");
        assertSegmentStatusOk(log, 1);

        // first attempt and the step that failed in it
        assertSegmentStatusError(log, 2);
        assertSegmentLog(log, 3, "[ERROR] (concord.yaml): Error @ line: 15, col: 11. FAIL");
        assertSegmentMultilineLog(log, 3, "[ERROR] Call stack:\n" +
                "(concord.yaml) @ line: 3, col: 7, thread: 0, flow: inner");
        assertSegmentStatusError(log, 3);

        // second attempt
        assertSegmentStatusOk(log, 4);
        assertSegmentLog(log, 5, "[INFO ] in inner flow");
        assertSegmentStatusOk(log, 5);

        assertNoMoreSegments();
    }

    @Test
    public void callWithErrorTest() throws Exception {
        deploy("logSegments/callWithError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // a failed call stays failed even when its error block succeeds
        assertSegmentStatusError(log, 1);

        // the failed step of the called flow
        assertSegmentLog(log, 2, "[ERROR] (concord.yaml): Error @ line: 13, col: 11. FAIL");
        assertSegmentMultilineLog(log, 2, "[ERROR] Call stack:\n" +
                "(concord.yaml) @ line: 3, col: 7, thread: 0, flow: inner");
        assertSegmentStatusError(log, 2);

        // error block
        assertSegmentStatusOk(log, 3);
        assertSegmentLog(log, 4, "[INFO ] in error block");
        assertSegmentStatusOk(log, 4);

        assertNoMoreSegments();
    }

    @Test
    public void callWithErrorThrowErrorTest() throws Exception {
        deploy("logSegments/callWithErrorThrowError");

        save(ProcessConfiguration.builder()
                .build());

        try {
            runWithSegments();
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // the call fails: its error block fails
        assertSegmentStatusError(runtime.lastLog(), 1);

        // the failed step of the called flow
        assertSegmentLog(runtime.lastLog(), 2, "[ERROR] (concord.yaml): Error @ line: 13, col: 11. FAIL");
        assertSegmentMultilineLog(runtime.lastLog(), 2, "[ERROR] Call stack:\n" +
                "(concord.yaml) @ line: 3, col: 7, thread: 0, flow: inner");
        assertSegmentStatusError(runtime.lastLog(), 2);

        // error block
        assertSegmentStatusError(runtime.lastLog(), 3);

        assertSegmentLog(runtime.lastLog(), 4, "[INFO ] in error block");
        assertSegmentStatusOk(runtime.lastLog(), 4);

        assertSegmentLog(runtime.lastLog(), 5, "[ERROR] (concord.yaml): Error @ line: 8, col: 11. FAIL");
        assertSegmentStatusError(runtime.lastLog(), 5);

        assertNoMoreSegments();
    }

    @Test
    public void taskSensitiveDataInName() throws Exception {
        deploy("logSegments/taskWithSensitiveDataInName");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentName(1, "My masked password: ******");
        assertSegmentLog(log, 1, "[INFO ] Still masked: ******");
        assertSegmentStatusOk(log, 1);
        assertNoMoreSegments();
    }

    private void assertSegmentName(int segmentId, String expectedName) {
        assertEquals(expectedName, runtime.testLoggingClient().getSegmentName(segmentId));
    }

    private static void assertSystemSegment(byte[] log, String message) throws IOException {
        assertSegmentLog(log, 0, message);
    }

    private static void assertSegmentLogPattern(byte[] log, int segmentId, Integer len, String messagePattern) throws IOException {
        // RUNNING
        String segmentLog;
        if (len != null) {
            segmentLog = Pattern.quote("|%d|%d|%d|".formatted(len, segmentId, LogSegmentStatus.RUNNING.id())) + "\\d+\\|\\d+\\|" + ".*" + messagePattern;
        } else {
            segmentLog = "\\|.*\\|%d\\|%d\\|".formatted(segmentId, LogSegmentStatus.RUNNING.id()) + "\\d+\\|\\d+\\|" + ".*" + messagePattern;
        }
        assertLog(log, ".*" + segmentLog);

        byte[] logWithoutSegments = removePattern(logWithoutSegmentsHolder.get(), segmentLog + "\n");
        logWithoutSegmentsHolder.set(logWithoutSegments);
    }

    private static void assertSegmentMultilineLog(byte[] log, int segmentId, String message) throws IOException {
        assertSegmentLog(log, segmentId, message, true);
    }

    private static void assertSegmentLog(byte[] log, int segmentId, String message) throws IOException {
        assertSegmentLog(log, segmentId, message, false);
    }

    private static void assertSegmentLog(byte[] log, int segmentId, String message, boolean multiLine) throws IOException {
        String messagePattern = Pattern.quote(message);
        int len = "2024-06-04T12:11:36.281+0000 ".length() + message.length() + "\n".length();

        // RUNNING
        String segmentLog = Pattern.quote("|%d|%d|%d|".formatted(len, segmentId, LogSegmentStatus.RUNNING.id())) + "\\d+\\|\\d+\\|" + ".*" + messagePattern;
        if (multiLine) {
            assertMultiLineLog(log, ".*" + segmentLog);
        } else {
            assertLog(log, ".*" + segmentLog);
        }

        byte[] logWithoutSegments = removePattern(logWithoutSegmentsHolder.get(), segmentLog + "\n");
        logWithoutSegmentsHolder.set(logWithoutSegments);
    }

    private static void assertSegmentStatusOk(byte[] log, int segmentId) throws IOException {
        assertSegmentStatus(log, segmentId, LogSegmentStatus.OK);
    }

    private static void assertSegmentStatusError(byte[] log, int segmentId) throws IOException {
        assertSegmentStatus(log, segmentId, LogSegmentStatus.ERROR);
    }

    private static void assertSegmentStatusSuspended(byte[] log, int segmentId) throws IOException {
        assertSegmentStatus(log, segmentId, LogSegmentStatus.SUSPENDED);
    }

    private static void assertSegmentStatusRunning(byte[] log, int segmentId) throws IOException {
        assertSegmentStatus(log, segmentId, LogSegmentStatus.RUNNING);
    }

    private static void assertSegmentStatus(byte[] log, int segmentId, LogSegmentStatus status) throws IOException {
        // final statuses carry the time of the status (epoch millis), RUNNING has no body
        String statusLog;
        if (status == LogSegmentStatus.RUNNING) {
            statusLog = Pattern.quote("|0|%d|%d|".formatted(segmentId, status.id())) + "\\d+\\|\\d+\\|";
        } else {
            statusLog = "\\|13\\|" + Pattern.quote("%d|%d|".formatted(segmentId, status.id())) + "\\d+\\|\\d+\\|\\d{13}";
        }
        assertLog(log, ".*" + statusLog + ".*");

        byte[] logWithoutSegments = removePattern(logWithoutSegmentsHolder.get(), statusLog);
        logWithoutSegmentsHolder.set(logWithoutSegments);
    }

    private static void assertNoMoreSegments() {
        byte[] logWithoutSegment = logWithoutSegmentsHolder.get();
        assertEquals(0, logWithoutSegment.length, () -> "Unexpected segments:\n" + new String(logWithoutSegment));
    }

    private byte[] runWithSegments() throws Exception {
        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        try {
            byte[] log = run(runnerCfg);
            logWithoutSegmentsHolder.set(Arrays.copyOf(log, log.length));
            return log;
        } catch (Exception e){
            logWithoutSegmentsHolder.set(Arrays.copyOf(runtime.lastLog(), runtime.lastLog().length));
            throw e;
        }
    }

    private byte[] resumeWithSegments(String eventName) throws Exception {
        StateManager.saveResumeEvent(runtime.workDir(), eventName);

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        try {
            byte[] log = run(runnerCfg);
            logWithoutSegmentsHolder.set(Arrays.copyOf(log, log.length));
            return log;
        } catch (Exception e){
            logWithoutSegmentsHolder.set(Arrays.copyOf(runtime.lastLog(), runtime.lastLog().length));
            throw e;
        }
    }

    private static byte[] removePattern(byte[] inputBytes, String str) {
        String inputString = new String(inputBytes, StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile(str).matcher(inputString);

        String resultString = matcher.replaceFirst("");

        return resultString.getBytes(StandardCharsets.UTF_8);
    }


    private void deploy(String name) throws URISyntaxException, IOException {
        runtime.deploy(name);
    }

    private void save(ProcessConfiguration cfg) {
        runtime.save(cfg);
    }

    private byte[] run() throws Exception {
        return runtime.run();
    }

    private byte[] run(RunnerConfiguration baseCfg) throws Exception {
        return runtime.run(baseCfg);
    }

    private byte[] resume(String eventName, ProcessConfiguration cfg) throws Exception {
        return runtime.resume(eventName, cfg);
    }
}
