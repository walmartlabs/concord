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

import com.walmartlabs.concord.agent.postprocessing.JobPostProcessor;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.project.InternalConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class DockerRunnerJobExecutor extends RunnerJobExecutor {

    private final RunnerJobExecutorConfiguration runnerCfg;
    private final DockerRunnerJobExecutorConfiguration dockerRunnerCfg;

    public DockerRunnerJobExecutor(RunnerJobExecutorConfiguration runnerCfg,
                                   DockerRunnerJobExecutorConfiguration dockerRunnerCfg,
                                   DependencyManager dependencyManager,
                                   DefaultDependencies defaultDependencies,
                                   List<JobPostProcessor> postProcessors,
                                   ProcessPool processPool,
                                   ExecutorService executor) {

        super(runnerCfg, dependencyManager, defaultDependencies, postProcessors, processPool, executor);

        this.runnerCfg = runnerCfg;
        this.dockerRunnerCfg = dockerRunnerCfg;
    }

    @Override
    protected ProcessPool.ProcessEntry buildProcessEntry(RunnerJob job, Collection<Path> resolvedDeps) throws Exception {
        Path procDir = IOUtils.createTempDir("onetime");
        String[] cmd = createCmd(job, resolvedDeps, procDir);
        return startOneTime(job, cmd, procDir);
    }

    private String[] createCmd(RunnerJob job, Collection<Path> deps, Path procDir) throws IOException {
        Map<String, Object> containerCfg = getContainerCfg(job);

        String javaCmd = DockerCommandBuilder.getJavaCmd();

        Files.createDirectories(procDir.resolve(".deps"));

        // copy deps into workspace
        for (Path src : deps) {
            Path dst = Paths.get(src.toString().replace(dependencyManager.getLocalCacheDir().toString(), procDir.resolve(".deps").toString()));
            IOUtils.copy(src, dst);
        }

        deps = deps.stream()
                .map(d -> d.toString().replace(dependencyManager.getLocalCacheDir().toString(), DockerCommandBuilder.getWorkspaceDir() + "/" + ".deps"))
                .map(d -> Paths.get(d))
                .collect(Collectors.toList());

        Path depsFile = storeDeps(deps);
        depsFile = DockerCommandBuilder.getDependencyListsDir().resolve(depsFile.getFileName());

        Path runnerPath = DockerCommandBuilder.getRunnerPath(runnerCfg.getRunnerPath());
        Path runnerDir = DockerCommandBuilder.getWorkspaceDir().resolve(InternalConstants.Files.PAYLOAD_DIR_NAME);

        RunnerCommandBuilder runner = new RunnerCommandBuilder()
                .javaCmd(javaCmd)
                .workDir(job.getPayloadDir())
                .procDir(runnerDir)
                .agentId(runnerCfg.getAgentId())
                .serverApiBaseUrl(runnerCfg.getServerApiBaseUrl())
                .securityManagerEnabled(runnerCfg.isRunnerSecurityManagerEnabled())
                .dependencies(depsFile)
                .debug(job.isDebugMode())
                .runnerPath(runnerPath);

        return new DockerCommandBuilder(job.getLog(), dockerRunnerCfg.javaPath, containerCfg)
                .procDir(procDir)
                .instanceId(job.getInstanceId())
                .dependencyListsDir(runnerCfg.getDependencyListDir())
                .dependencyCacheDir(dockerRunnerCfg.dependencyCacheDir)
                .runnerPath(runnerCfg.getRunnerPath())
                .args(runner.build())
                .extraEnv(IOUtils.TMP_DIR_KEY, "/tmp")
                .extraEnv("DOCKER_HOST", dockerRunnerCfg.dockerHost)
                .extraEnv("HOST_PROC_DIR", procDir)
                .extraEnv("CONCORD_TX_ID", job.getInstanceId())
                .extraVolumes(runnerCfg.getExtraDockerVolumes())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getContainerCfg(RunnerJob job) {
        Map<String, Object> cfg = job.getCfg();
        Map<String, Object> result = (Map<String, Object>) cfg.get(InternalConstants.Request.CONTAINER);
        if (result == null) {
            throw new IllegalArgumentException("Docker runner without container configuration");
        }
        return result;
    }

    public static class DockerRunnerJobExecutorConfiguration {
        private final String dockerHost;
        private final Path dependencyCacheDir;
        private final Path javaPath;

        public DockerRunnerJobExecutorConfiguration(String dockerHost, Path dependencyCacheDir, Path javaPath) {
            this.dockerHost = dockerHost;
            this.dependencyCacheDir = dependencyCacheDir;
            this.javaPath = javaPath;
        }
    }
}
