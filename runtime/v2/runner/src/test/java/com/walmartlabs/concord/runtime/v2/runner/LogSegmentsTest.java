package com.walmartlabs.concord.runtime.v2.runner;

import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

public class LogSegmentsTest extends AbstractTest {

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
    public void testContextInjectorWithSegmentedLogger() throws Exception {
        deploy("injectorTest");

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .build())
                .build();

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run(runnerCfg);

        assertLog(log, Pattern.quote("|0|1|1|0|0||70|2|0|0|0|") + ".*done!.*");
        assertLog(log, Pattern.quote("|0|2|1|0|0|"));
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
