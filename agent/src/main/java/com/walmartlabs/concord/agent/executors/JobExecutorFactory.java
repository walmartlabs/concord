package com.walmartlabs.concord.agent.executors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.agent.ConfiguredJobRequest;
import com.walmartlabs.concord.agent.JobRequest;
import com.walmartlabs.concord.agent.cfg.*;
import com.walmartlabs.concord.agent.executors.runner.DefaultDependencies;
import com.walmartlabs.concord.agent.executors.runner.ProcessPool;
import com.walmartlabs.concord.agent.executors.runner.RunnerJobExecutor;
import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.remote.AttachmentsUploader;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class JobExecutorFactory {

    private final AgentConfiguration agentCfg;
    private final ServerConfiguration serverCfg;
    private final DockerConfiguration dockerCfg;
    private final PreForkConfiguration preForkCfg;

    private final RuntimeConfiguration runtimeCfg;

    private final DependencyManager dependencyManager;
    private final DefaultDependencies defaultDependencies;
    private final ProcessPool processPool;
    private final ProcessLog processLog;
    private final AttachmentsUploader attachmentsUploader;
    private final ProcessLogFactory processLogFactory;

    private final ExecutorService executor;

    @Inject
    public JobExecutorFactory(AgentConfiguration agentCfg,
                              ServerConfiguration serverCfg,
                              DockerConfiguration dockerCfg,
                              PreForkConfiguration preForkCfg,
                              RuntimeConfiguration runtimeCfg,
                              DependencyManager dependencyManager,
                              DefaultDependencies defaultDependencies,
                              ProcessPool processPool,
                              ProcessLog processLog,
                              AttachmentsUploader attachmentsUploader,
                              ProcessLogFactory processLogFactory) {

        this.agentCfg = agentCfg;
        this.serverCfg = serverCfg;
        this.dockerCfg = dockerCfg;
        this.preForkCfg = preForkCfg;
        this.runtimeCfg = runtimeCfg;
        this.dependencyManager = dependencyManager;
        this.defaultDependencies = defaultDependencies;
        this.processPool = processPool;
        this.processLog = processLog;
        this.attachmentsUploader = attachmentsUploader;
        this.processLogFactory = processLogFactory;

        this.executor = Executors.newCachedThreadPool();
    }

    public JobExecutor create(JobRequest.Type jobType) {
        if (jobType != JobRequest.Type.RUNNER) {
            throw new RuntimeException("Unsupported job type: " + jobType);
        }

        return jobRequest -> {
            String runtimeName = getRuntimeName(jobRequest);

            RuntimeConfiguration.Entry runtimeCfg = this.runtimeCfg.getForRuntime(runtimeName)
                    .orElseThrow(() -> new IllegalStateException("Runner configuration for '%s' not found.".formatted(runtimeName)));

            processLog.info("Runtime: {}", runtimeName);

            RunnerJobExecutor.RunnerJobExecutorConfiguration runnerExecutorCfg = RunnerJobExecutor.RunnerJobExecutorConfiguration.builder()
                    .agentId(agentCfg.getAgentId())
                    .serverApiBaseUrl(serverCfg.getApiBaseUrl())
                    .javaCmd(runtimeCfg.javaCmd())
                    .jvmParams(runtimeCfg.jvmParams())
                    .dependencyListDir(agentCfg.getDependencyListsDir())
                    .dependencyCacheDir(agentCfg.getDependencyCacheDir())
                    .dependencyResolveTimeout(agentCfg.getDependencyResolveTimeout())
                    .workDirBase(agentCfg.getWorkDirBase())
                    .runnerPath(runtimeCfg.path())
                    .runnerCfgDir(runtimeCfg.cfgDir())
                    .runnerSecurityManagerEnabled(false) // SecurityManager is deprecated and should not be used
                    .runnerMainClass(runtimeCfg.mainClass())
                    .extraDockerVolumes(dockerCfg.getExtraVolumes())
                    .exposeDockerDaemon(dockerCfg.exposeDockerDaemon())
                    .maxHeartbeatInterval(serverCfg.getMaxNoHeartbeatInterval())
                    .segmentedLogs(runtimeCfg.segmentedLogs())
                    .workDirMasking(agentCfg.isWorkDirMaskings())
                    .persistentWorkDir(runtimeCfg.persistentWorkDir())
                    .preforkEnabled(preForkCfg.isEnabled())
                    .cleanRunnerDescendants(runtimeCfg.cleanRunnerDescendants())
                    .build();

            JobExecutor delegate = new RunnerJobExecutor(runnerExecutorCfg, dependencyManager, defaultDependencies, attachmentsUploader, processPool, processLogFactory, executor);
            return delegate.exec(jobRequest);
        };
    }

    private static String getRuntimeName(ConfiguredJobRequest req) {
        return MapUtils.getString(req.getProcessCfg(), Constants.Request.RUNTIME_KEY, "concord-v1");
    }
}
