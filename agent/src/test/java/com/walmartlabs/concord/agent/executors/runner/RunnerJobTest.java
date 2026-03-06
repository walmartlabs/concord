package com.walmartlabs.concord.agent.executors.runner;

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

import com.walmartlabs.concord.agent.JobRequest;
import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.imports.Imports;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

public class RunnerJobTest {

    @Test
    public void fromUsesSessionBackedRunnerLogFactoryMethod() throws Exception {
        var runnerExecutorCfg = mock(RunnerJobExecutor.RunnerJobExecutorConfiguration.class);
        when(runnerExecutorCfg.segmentedLogs()).thenReturn(true);
        when(runnerExecutorCfg.agentId()).thenReturn("agent");
        when(runnerExecutorCfg.serverApiBaseUrl()).thenReturn("http://example.com");
        when(runnerExecutorCfg.maxHeartbeatInterval()).thenReturn(60_000L);
        when(runnerExecutorCfg.extraDockerVolumes()).thenReturn(Collections.emptyList());
        when(runnerExecutorCfg.exposeDockerDaemon()).thenReturn(true);
        when(runnerExecutorCfg.dependencyCacheDir()).thenReturn(Files.createTempDirectory("runner-deps"));
        when(runnerExecutorCfg.workDirMasking()).thenReturn(true);

        var processLogFactory = mock(ProcessLogFactory.class);
        var runnerLog = mock(ProcessLog.class);
        when(processLogFactory.createRunnerLog(any(), eq(true))).thenReturn(runnerLog);

        var payloadDir = Files.createTempDirectory("runner-job");
        var instanceId = UUID.randomUUID();
        var jobRequest = new TestJobRequest(JobRequest.Type.RUNNER, instanceId, payloadDir, "org",
                "https://example.com/repo.git", "/", "abc123", "main", null, Imports.builder().build(), "session");

        var job = RunnerJob.from(runnerExecutorCfg, jobRequest, processLogFactory);

        assertSame(runnerLog, job.getLog());
        verify(processLogFactory, times(1)).createRunnerLog(instanceId, true);
    }

    private static class TestJobRequest extends JobRequest {

        private TestJobRequest(Type type,
                               java.util.UUID instanceId,
                               java.nio.file.Path payloadDir,
                               String orgName,
                               String repoUrl,
                               String repoPath,
                               String commitId,
                               String repoBranch,
                               String secretName,
                               Imports imports,
                               String sessionToken) {

            super(type, instanceId, payloadDir, orgName, repoUrl, repoPath, commitId, repoBranch, secretName, imports, sessionToken);
        }
    }
}
