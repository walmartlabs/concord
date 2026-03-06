package com.walmartlabs.concord.agent;

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

import com.walmartlabs.concord.agent.guice.AgentImportManager;
import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.logging.RecordingLogAppender;
import com.walmartlabs.concord.agent.logging.RemoteProcessLog;
import com.walmartlabs.concord.agent.remote.ProcessStatusUpdater;
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.imports.Imports;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WorkerLoggingTest {

    @Test
    public void workerStageMessagesAreLoggedBeforeRunnerStartup() throws Exception {
        var appender = new RecordingLogAppender();
        var instanceId = UUID.randomUUID();
        var payloadDir = Files.createTempDirectory("worker-logging");

        var repositoryManager = mock(RepositoryManager.class);
        var importManager = mock(AgentImportManager.class);
        var executor = mock(JobExecutor.class);
        var completionCallback = mock(Worker.CompletionCallback.class);
        var stateFetcher = mock(StateFetcher.class);
        var processStatusUpdater = mock(ProcessStatusUpdater.class);
        var jobInstance = mock(JobInstance.class);

        var logSeenBeforeExec = new AtomicReference<String>();
        when(executor.exec(any())).thenAnswer(invocation -> {
            logSeenBeforeExec.set(appender.systemLog());
            return jobInstance;
        });

        var request = new JobRequest(JobRequest.Type.RUNNER, instanceId, payloadDir, "org",
                "https://example.com/repo.git", "/", "abc123", "main", null, Imports.builder().build(), "session");

        var worker = new Worker(repositoryManager, importManager, executor, completionCallback, stateFetcher,
                processStatusUpdater, new RemoteProcessLog(instanceId, appender), request);

        worker.run();

        var startupLog = logSeenBeforeExec.get();
        assertTrue(startupLog.contains("Exporting the repository data: https://example.com/repo.git @ main:abc123, path: /"));
        assertTrue(startupLog.contains("Repository data export took"));
        assertTrue(startupLog.contains("Downloading the process state..."));
        assertTrue(startupLog.contains("Process state download took"));

        verify(jobInstance, times(1)).waitForCompletion();
        verify(processStatusUpdater, times(1)).update(instanceId, StatusEnum.FINISHED);
        verify(completionCallback, times(1)).onStatusChange(StatusEnum.FINISHED);
    }
}
