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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

public class LogSegmentsTest extends AbstractTest {

    @Test
    public void testSystemOutRedirectInScripts() throws Exception {
        deploy("systemOutRedirect");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = runWithSegments();
        assertSegmentOk(log, 1, "[INFO ] System.out in a script");
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

        assertSegmentError(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. BOOM");
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

        assertSegmentError(lastLog, 1, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentError(lastLog, 2, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentError(lastLog, 3, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentError(lastLog, 4, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentError(lastLog, 5, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
        assertSegmentError(lastLog, 6, "[ERROR] (concord.yaml): Error @ line: 3, col: 7. Error during execution of 'faultyTask' task: boom!");
    }

    private static void assertSegmentOk(byte[] log, int segmentId, String message) throws IOException {
        assertSegment(log, segmentId, message, LogSegmentStatus.OK);
    }

    private static void assertSegmentError(byte[] log, int segmentId, String message) throws IOException {
        assertSegment(log, segmentId, message, LogSegmentStatus.ERROR);
    }

    private static void assertSegment(byte[] log, int segmentId, String message, LogSegmentStatus status) throws IOException {
        String messagePattern = "";
        int len = 0;
        if (message != null) {
            len = "2024-06-04T12:11:36.281+0000 ".length() + message.length() + 1;
            messagePattern = Pattern.quote(message);
        }

        // RUNNING
        assertLog(log, ".*" + Pattern.quote("|%d|%d|%d|".formatted(len, segmentId, LogSegmentStatus.RUNNING.id())) + ".*" + messagePattern);

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
