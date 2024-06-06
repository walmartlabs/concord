package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class LogSegmentsTest extends AbstractTest {

    @Test
    public void taskTest() throws Exception {
        deploy("logSegments/task");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegment(log, 1, "[INFO ] Message");
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

        // second
        assertSegment(log, 2, "[INFO ] ConditionallyFailTask: ok");

        // TODO: maybe into task segment?
        assertSystemSegment(log, "[WARN ] Last error: java.lang.RuntimeException: boom!. Waiting for 1000ms before retry (attempt #0)");
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

        // error block
        assertSegment(log, 2, "[INFO ] in error block");
    }

    @Test
    public void taskLoopSerialTest() throws Exception {
        deploy("logSegments/taskLoopSerial");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegment(log, 1, "[INFO ] test 1");
        assertSegment(log, 2, "[INFO ] test 2");
        assertSegment(log, 3, "[INFO ] test 3");
    }

    @Test
    public void taskLoopParallelTest() throws Exception {
        deploy("logSegments/taskLoopParallel");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegment(log, 1, "[INFO ] test");
        assertSegment(log, 2, "[INFO ] test");
        assertSegment(log, 3, "[INFO ] test");
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

        assertSegment(lastLog, 1, "[INFO ] ConditionallyFailTask: ok");
        assertSegmentLog(lastLog, 2, "[INFO ] ConditionallyFailTask: fail");
        assertErrorSegment(lastLog, 2, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. boom!");
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

        assertSegmentLog(lastLog, 1, "[INFO ] test");
        assertErrorSegment(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Can't find a variable 'undefined' used in '${undefined}'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'");
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
    }

    @Test
    public void taskSuspendTest() throws Exception {
        deploy("logSegments/taskWithSuspend");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegment(log, 1, "[INFO ] will suspend with event: 'sleepingBeauty'", RunnerLogger.SegmentStatus.SUSPENDED);

        log = resumeWithSegments("sleepingBeauty");

        assertSegment(log, 1, null, RunnerLogger.SegmentStatus.OK);
    }

    @Test
    public void taskReentrantTest() throws Exception {
        deploy("logSegments/taskWithReentrantSuspend");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        assertSegment(log, 1, "[INFO ] execute {action=boo}", RunnerLogger.SegmentStatus.SUSPENDED);

        log = resumeWithSegments(ReentrantTaskExample.EVENT_NAME);

        assertSegment(log, 1, null, RunnerLogger.SegmentStatus.OK);
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
        assertErrorSegment(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
    }

    @Test
    public void taskErrorWithErrorTest() throws Exception {
        // task with error handler throws error
        deploy("logSegments/taskErrorWithError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();

        // task error into task segment
        assertSegment(log, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");

        // error handler logs
        assertSegment(log, 2, "[INFO ] in error block");
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
        assertErrorSegment(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Task not found: 'undefinedTask'");
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
    }

    @Test
    public void throwStepShouldContainErrorDescription() throws Exception {
        deploy("logSegments1");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        try {
            run(runnerCfg);
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // 83 log message length, 1 - segment id
        assertLog(lastLog, ".*" + Pattern.quote("|83|1|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. BOOM"));
    }

    @Test
    public void loopStepShouldLogErrorInProperLogSegment() throws Exception {
        deploy("logSegments2");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        try {
            run(runnerCfg);
            fail("exception expected");
        } catch (Exception e) {
            // ignore
        }

        // 129 log message length, 1, 2, 3, 4, 5, 6 - segment ids
        assertLog(lastLog, ".*" + Pattern.quote("|129|1|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|2|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|3|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|4|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|5|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
        assertLog(lastLog, ".*" + Pattern.quote("|129|6|") + ".*" + Pattern.quote("[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!"));
    }

    @Test
    public void logSegmentsInTaskLoop() throws Exception {
        deploy("logSegments3");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        byte[] log = run(runnerCfg);

        // 39 log message length, 1, 2, 3, 4, 5, 6 - segment ids
        assertLog(log, ".*" + Pattern.quote("|39|1|") + ".*" + Pattern.quote("[INFO ] 1"));
        assertLog(log, ".*" + Pattern.quote("|39|2|") + ".*" + Pattern.quote("[INFO ] 2"));
        assertLog(log, ".*" + Pattern.quote("|39|3|") + ".*" + Pattern.quote("[INFO ] 3"));
        assertLog(log, ".*" + Pattern.quote("|39|4|") + ".*" + Pattern.quote("[INFO ] 4"));
        assertLog(log, ".*" + Pattern.quote("|39|5|") + ".*" + Pattern.quote("[INFO ] 5"));
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

    private static void assertSegment(byte[] log, int segmentId, String message) throws IOException {
        assertSegment(log, segmentId, message, RunnerLogger.SegmentStatus.OK);
    }

    private static void assertErrorSegment(byte[] log, int segmentId, String message) throws IOException {
        assertSegment(log, segmentId, message, RunnerLogger.SegmentStatus.ERROR);
    }

    private static void assertSystemSegment(byte[] log, String message) throws IOException {
        assertSegment(log, 0, message, null);
    }

    private static void assertSegmentLog(byte[] log, int segmentId, String message) throws IOException {
        assertSegment(log, segmentId, message, null);
    }

    private static void assertSegment(byte[] log, int segmentId, String message, RunnerLogger.SegmentStatus status) throws IOException {
        String messagePattern = "";
        int len = 0;
        if (message != null) {
            len = "2024-06-04T12:11:36.281+0000 ".length() + message.length() + 1;
            messagePattern = Pattern.quote(message);
        }

        // RUNNING
        assertLog(log, ".*" + Pattern.quote("|%d|%d|%d|".formatted(len, segmentId, RunnerLogger.SegmentStatus.RUNNING.id())) + ".*" + messagePattern);

        // CLOSED with status
        if (status != null) {
            assertLog(log, ".*" + Pattern.quote("|%d|%d|%d|".formatted(0, segmentId, status.id())) + ".*");
        }
    }

    private byte[] runWithSegments() throws Exception {
        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        return run(runnerCfg);
    }

    private byte[] resumeWithSegments(String eventName) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName);

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .segmentedLogs(true)
                        .build())
                .build();

        return run(runnerCfg);
    }
}
