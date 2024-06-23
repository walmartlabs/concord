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
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class LogSegmentsTest extends AbstractTest {

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

        assertSegmentLog(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. BOOM");
        assertSegmentStatusError(lastLog, 1);
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

        assertSegmentLog(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(lastLog, 1);

        assertSegmentLog(lastLog, 2, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(lastLog, 2);

        assertSegmentLog(lastLog, 3, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(lastLog, 3);

        assertSegmentLog(lastLog, 4, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(lastLog, 4);

        assertSegmentLog(lastLog, 5, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(lastLog, 5);

        assertSegmentLog(lastLog, 6, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(lastLog, 6);
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

        // first try
        assertSegmentLog(log, 1, "[INFO ] ConditionallyFailTask: fail");
        assertSegmentLog(log, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
        assertSegmentStatusError(log, 1);

        // second
        assertSegmentLog(log, 2, "[INFO ] ConditionallyFailTask: ok");
        assertSegmentStatusOk(log, 2);

        // TODO: maybe into task segment?
        assertSystemSegment(log, "[WARN ] Last error: java.lang.RuntimeException: boom!. Waiting for 1000ms before retry (attempt #0)");

        assertNoMoreSegments();
    }

    @Test
    public void taskErrorWithReturn() throws Exception {
        deploy("logSegments/taskErrorWithReturn");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // first try
        assertSegmentLog(log, 1, "[INFO ] will fail with error");
        assertSegmentLog(log, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(log, 1);

        // error block
        assertSegmentLog(log, 2, "[INFO ] in error block");
        assertSegmentStatusOk(log, 2);

        assertNoMoreSegments();
    }

    @Test
    public void taskLoopSerialTest() throws Exception {
        deploy("logSegments/taskLoopSerial");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] test 1");
        assertSegmentStatusOk(log, 1);

        assertSegmentLog(log, 2, "[INFO ] test 2");
        assertSegmentStatusOk(log, 2);

        assertSegmentLog(log, 3, "[INFO ] test 3");
        assertSegmentStatusOk(log, 3);

        assertNoMoreSegments();
    }

    @Test
    @IgnoreSerializationAssert
    public void taskLoopParallelTest() throws Exception {
        deploy("logSegments/taskLoopParallel");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 1, "[INFO ] test");
        assertSegmentStatusOk(log, 1);

        assertSegmentLog(log, 2, "[INFO ] test");
        assertSegmentStatusOk(log, 2);

        assertSegmentLog(log, 3, "[INFO ] test");
        assertSegmentStatusOk(log, 3);

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

        assertSegmentLog(lastLog, 1, "[INFO ] ConditionallyFailTask: ok");
        assertSegmentStatusOk(lastLog, 1);

        assertSegmentLog(lastLog, 2, "[INFO ] ConditionallyFailTask: fail");
        assertSegmentLog(lastLog, 2, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
        assertSegmentStatusError(lastLog, 2);

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
        assertSegmentLog(lastLog, 1, "[INFO ] test");
        assertSegmentLog(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Can't find a variable 'undefined' used in '${undefined}'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
        assertSegmentStatusError(lastLog, 1);

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

        assertSystemSegment(lastLog, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Can't find a variable 'undefined' used in '${undefined}'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");

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
        assertSegmentLog(lastLog, 1, "[INFO ] will fail with error");
        assertSegmentLog(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(lastLog, 1);

        assertNoMoreSegments();
    }

    @Test
    public void taskErrorWithErrorTest() throws Exception {
        // task with error handler throws error
        deploy("logSegments/taskErrorWithError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // task error into task segment
        assertSegmentLog(log, 1, "[INFO ] will fail with error");
        assertSegmentLog(log, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentStatusError(log, 1);

        // error handler logs
        assertSegmentLog(log, 2, "[INFO ] in error block");
        assertSegmentStatusOk(log, 2);

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

        assertSystemSegment(lastLog, "[ERROR] (concord.yaml): Error @ line: 4, col: 7. Can't find a variable 'undefined' used in '${undefined}'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
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
        assertSegmentLog(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Task not found: 'undefinedTask'");
        assertSegmentStatusError(lastLog, 1);

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

        // logs to the system segment
        assertSystemSegment(lastLog, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Can't find a variable 'undefined' used in '${undefined}'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
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
        assertSystemSegment(lastLog, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Can't find a variable 'undefined' used in '${undefined}'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
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
        assertSystemSegment(lastLog, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Can't find a variable 'undefined' used in '${undefined}'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
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

        assertSegmentLog(lastLog, 1, "[ERROR] (concord.yml): Error @ line: 3, col: 7. Unknown language 'js234'. Check process dependencies.");
        assertSegmentStatusError(lastLog, 1);
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

        assertSegmentLogPattern(lastLog, 1, null, Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. TypeError: invokeMember (unknownMethod) on") + ".*");
        assertSegmentStatusError(lastLog, 1);
    }

    @Test
    public void callTest() throws Exception {
        deploy("logSegments/call");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 2, "[INFO ] in inner flow");
        assertSegmentStatusOk(log, 2);
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

        assertSegmentLog(log, 2, "[INFO ] in inner flow, item: 1");
        assertSegmentStatusOk(log, 2);
        // (named call step)
        assertSegmentStatusOk(log, 1);

        assertSegmentLog(log, 4, "[INFO ] in inner flow, item: 2");
        assertSegmentStatusOk(log, 4);
        // (named call step)
        assertSegmentStatusOk(log, 3);

        assertSegmentLog(log, 6, "[INFO ] in inner flow, item: 3");
        assertSegmentStatusOk(log, 6);
        // (named call step)
        assertSegmentStatusOk(log, 5);

        assertNoMoreSegments();
    }

    @Test
    public void callWithRetryTest() throws Exception {
        deploy("logSegments/callWithRetry");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 2, "[ERROR] (concord.yaml): Error @ line: 15, col: 11. FAIL");
        assertSegmentMultilineLog(log, 2, "[ERROR] Call stack:\n" +
                "(concord.yaml) @ line: 3, col: 7, thread: 0, flow: inner");
        assertSegmentStatusError(log, 2);
        assertSegmentStatusError(log, 1);

        assertSystemSegment(log, "[WARN ] Last error: com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException: FAIL. Waiting for 1000ms before retry (attempt #0)");

        assertSegmentLog(log, 4, "[INFO ] in inner flow");
        assertSegmentStatusOk(log, 4);
        assertSegmentStatusOk(log, 3);

        assertNoMoreSegments();
    }

    @Test
    public void callWithErrorTest() throws Exception {
        deploy("logSegments/callWithError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegmentLog(log, 2, "[ERROR] (concord.yaml): Error @ line: 13, col: 11. FAIL");
        assertSegmentMultilineLog(log, 2, "[ERROR] Call stack:\n" +
                "(concord.yaml) @ line: 3, col: 7, thread: 0, flow: inner");
        assertSegmentStatusError(log, 2);
        assertSegmentStatusError(log, 1);

        assertSegmentLog(log, 3, "[INFO ] in error block");
        assertSegmentStatusOk(log, 3);

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

        assertSegmentLog(lastLog, 2, "[ERROR] (concord.yaml): Error @ line: 13, col: 11. FAIL");
        assertSegmentMultilineLog(lastLog, 2, "[ERROR] Call stack:\n" +
                "(concord.yaml) @ line: 3, col: 7, thread: 0, flow: inner");
        assertSegmentStatusError(lastLog, 2);
        assertSegmentStatusError(lastLog, 1);

        assertSegmentLog(lastLog, 3, "[INFO ] in error block");
        assertSegmentStatusOk(lastLog, 3);

        assertSegmentLog(lastLog, 4, "[ERROR] (concord.yaml): Error @ line: 8, col: 11. FAIL");
        assertSegmentStatusError(lastLog, 4);

        assertNoMoreSegments();
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
        String statusLog = Pattern.quote("|%d|%d|%d|".formatted(0, segmentId, status.id())) + "\\d+\\|\\d+\\|";
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
            logWithoutSegmentsHolder.set(Arrays.copyOf(lastLog, lastLog.length));
            throw e;
        }
    }

    private byte[] resumeWithSegments(String eventName) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName);

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
            logWithoutSegmentsHolder.set(Arrays.copyOf(lastLog, lastLog.length));
            throw e;
        }
    }

    private static byte[] removePattern(byte[] inputBytes, String str) {
        String inputString = new String(inputBytes, StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile(str).matcher(inputString);

        String resultString = matcher.replaceFirst("");

        return resultString.getBytes(StandardCharsets.UTF_8);
    }
}
