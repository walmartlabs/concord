package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.agent.cfg.DockerConfiguration;
import com.walmartlabs.concord.agent.cfg.RunnerV1Configuration;
import com.walmartlabs.concord.agent.cfg.ServerConfiguration;
import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.postprocessing.JobPostProcessor;
import com.walmartlabs.concord.dependencymanager.DependencyManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Named
@Singleton
public class RunnerJobExecutorProvider implements Provider<JobExecutor> {

    private final AgentConfiguration agentCfg;
    private final ServerConfiguration serverCfg;
    private final DockerConfiguration dockerCfg;
    private final RunnerV1Configuration runnerV1Cfg;

    private final DependencyManager dependencyManager;
    private final DefaultDependencies defaultDependencies;
    private final List<JobPostProcessor> postProcessors;
    private final ProcessPool processPool;
    private final ProcessLogFactory processLogFactory;

    private final ExecutorService executor;

    @Inject
    public RunnerJobExecutorProvider(AgentConfiguration agentCfg,
                                     ServerConfiguration serverCfg,
                                     DockerConfiguration dockerCfg,
                                     RunnerV1Configuration runnerV1Cfg,
                                     DependencyManager dependencyManager,
                                     DefaultDependencies defaultDependencies,
                                     List<JobPostProcessor> postProcessors,
                                     ProcessPool processPool,
                                     ProcessLogFactory processLogFactory) {

        this.agentCfg = agentCfg;
        this.serverCfg = serverCfg;
        this.dockerCfg = dockerCfg;
        this.runnerV1Cfg = runnerV1Cfg;

        this.dependencyManager = dependencyManager;
        this.defaultDependencies = defaultDependencies;
        this.postProcessors = postProcessors;
        this.processPool = processPool;
        this.processLogFactory = processLogFactory;

        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public JobExecutor get() {
        RunnerJobExecutor.RunnerJobExecutorConfiguration runnerExecutorCfg = RunnerJobExecutor.RunnerJobExecutorConfiguration.builder()
                .agentId(agentCfg.getAgentId())
                .serverApiBaseUrl(serverCfg.getApiBaseUrl())
                .agentJavaCmd(runnerV1Cfg.getJavaCmd())
                .dependencyListDir(agentCfg.getDependencyListsDir())
                .dependencyCacheDir(agentCfg.getDependencyCacheDir())
                .runnerPath(runnerV1Cfg.getPath())
                .runnerCfgDir(runnerV1Cfg.getCfgDir())
                .runnerSecurityManagerEnabled(runnerV1Cfg.isSecurityManagerEnabled())
                .extraDockerVolumes(dockerCfg.getExtraVolumes())
                .maxHeartbeatInterval(serverCfg.getMaxNoHeartbeatInterval())
                .build();

        return new RunnerJobExecutor(runnerExecutorCfg, dependencyManager, defaultDependencies, postProcessors, processPool, processLogFactory, executor);
    }
}
