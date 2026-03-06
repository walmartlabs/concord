package com.walmartlabs.concord.agent.guice;

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

import com.walmartlabs.concord.agent.logging.CombinedLogAppender;
import com.walmartlabs.concord.agent.logging.RecordingLogAppender;
import com.walmartlabs.concord.agent.logging.StdOutLogAppender;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkerModuleTest {

    @Test
    public void workerProcessLogUsesProvidedAppenderComposition() throws Exception {
        var appender = new RecordingLogAppender();
        var combined = new CombinedLogAppender(Set.of(appender, new StdOutLogAppender()));
        var processLog = new WorkerModule("agent", UUID.randomUUID(), "session").getProcessLog(combined);

        var originalOut = System.out;
        var stdout = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            processLog.info("worker-stage message");
        } finally {
            System.setOut(originalOut);
        }

        assertTrue(appender.systemLog().contains("worker-stage message"));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("RUNNER: "));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("worker-stage message"));
    }
}
