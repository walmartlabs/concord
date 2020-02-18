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

import com.walmartlabs.concord.agent.ConfiguredJobRequest;
import com.walmartlabs.concord.agent.JobInstance;
import com.walmartlabs.concord.agent.JobRequest;
import com.walmartlabs.concord.agent.cfg.*;
import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.postprocessing.JobPostProcessor;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Named
@Singleton
public class RunnerJobExecutorProvider implements Provider<JobExecutor> {

    private final AgentConfiguration agentCfg;
    private final ServerConfiguration serverCfg;
    private final DockerConfiguration dockerCfg;

    private final RunnerV1Configuration runnerV1Cfg;
    private final RunnerV2Configuration runnerV2Cfg;

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
                                     RunnerV2Configuration runnerV2Cfg,
                                     DependencyManager dependencyManager,
                                     DefaultDependencies defaultDependencies,
                                     List<JobPostProcessor> postProcessors,
                                     ProcessPool processPool,
                                     ProcessLogFactory processLogFactory) {

        this.agentCfg = agentCfg;
        this.serverCfg = serverCfg;
        this.dockerCfg = dockerCfg;

        this.runnerV1Cfg = runnerV1Cfg;
        this.runnerV2Cfg = runnerV2Cfg;

        this.dependencyManager = dependencyManager;
        this.defaultDependencies = defaultDependencies;
        this.postProcessors = postProcessors;
        this.processPool = processPool;
        this.processLogFactory = processLogFactory;

        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public JobExecutor get() {
        return new JobExecutor() {
            @Override
            public JobRequest.Type acceptsType() {
                return JobRequest.Type.RUNNER;
            }

            @Override
            public JobInstance exec(ConfiguredJobRequest jobRequest) throws Exception {
                AbstractRunnerConfiguration runnerCfg = runnerV1Cfg;
                if (isV2(jobRequest)) {
                    runnerCfg = runnerV2Cfg;
                }

                jobRequest.getLog().info("Runtime: {}", runnerCfg.getRuntimeName());

                RunnerJobExecutor.RunnerJobExecutorConfiguration runnerExecutorCfg = RunnerJobExecutor.RunnerJobExecutorConfiguration.builder()
                        .agentId(agentCfg.getAgentId())
                        .serverApiBaseUrl(serverCfg.getApiBaseUrl())
                        .agentJavaCmd(runnerCfg.getJavaCmd())
                        .dependencyListDir(agentCfg.getDependencyListsDir())
                        .dependencyCacheDir(agentCfg.getDependencyCacheDir())
                        .runnerPath(runnerCfg.getPath())
                        .runnerCfgDir(runnerCfg.getCfgDir())
                        .runnerSecurityManagerEnabled(runnerCfg.isSecurityManagerEnabled())
                        .runnerMainClass(runnerCfg.getMainClass())
                        .extraDockerVolumes(dockerCfg.getExtraVolumes())
                        .maxHeartbeatInterval(serverCfg.getMaxNoHeartbeatInterval())
                        .build();

                JobExecutor delegate = new RunnerJobExecutor(runnerExecutorCfg, dependencyManager, defaultDependencies, postProcessors, processPool, processLogFactory, executor);
                return delegate.exec(jobRequest);
            }
        };
    }

    private static boolean isV2(ConfiguredJobRequest req) {
        Map<String, Object> m = req.getProcessCfg();
        String s = MapUtils.getString(m, Constants.Request.RUNTIME_KEY, "concord-v1"); // TODO constants
        return "concord-v2".equals(s);
    }
}
