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
import com.walmartlabs.concord.runner.model.RunnerConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
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
    protected ProcessPool.ProcessEntry buildProcessEntry(RunnerJob job) throws Exception {
        Path procDir = IOUtils.createTempDir("onetime");
        String[] cmd = createCmd(job, procDir);
        return startOneTime(job, cmd, procDir);
    }

    private String[] createCmd(RunnerJob job, Path procDir) throws IOException {
        job = job.withDependencies(collectDeps(job, procDir));

        Map<String, Object> containerCfg = getContainerCfg(job);

        String javaCmd = DockerCommandBuilder.getJavaCmd();

        Path runnerPath = DockerCommandBuilder.getRunnerPath(runnerCfg.runnerPath());
        Path runnerDir = DockerCommandBuilder.getWorkspaceDir().resolve(InternalConstants.Files.PAYLOAD_DIR_NAME);

        // we can't use "preforks" anyway, so let's store the runner's cfg directly in the payload dir
        Path runnerCfgPath = runnerDir.resolve(storeRunnerCfg(job.getPayloadDir(), job.getRunnerCfg()).getFileName());

        RunnerCommandBuilder runner = new RunnerCommandBuilder()
                .javaCmd(javaCmd)
                .workDir(job.getPayloadDir())
                .procDir(runnerDir)
                .runnerPath(runnerPath)
                .runnerCfgPath(runnerCfgPath);

        return new DockerCommandBuilder(job.getLog(), dockerRunnerCfg.javaPath, containerCfg)
                .procDir(procDir)
                .instanceId(job.getInstanceId())
                .dependencyListsDir(runnerCfg.dependencyListDir())
                .dependencyCacheDir(dockerRunnerCfg.dependencyCacheDir)
                .runnerPath(runnerCfg.runnerPath())
                .args(runner.build())
                .extraEnv(IOUtils.TMP_DIR_KEY, "/tmp")
                .extraEnv("DOCKER_HOST", dockerRunnerCfg.dockerHost)
                .extraEnv("HOST_PROC_DIR", procDir)
                .extraEnv("CONCORD_TX_ID", job.getInstanceId())
                .extraVolumes(runnerCfg.extraDockerVolumes())
                .build();
    }

    private Collection<String> collectDeps(RunnerJob job, Path procDir) throws IOException {
        RunnerConfiguration runnerCfg = job.getRunnerCfg();
        if (runnerCfg == null || runnerCfg.dependencies() == null) {
            return Collections.emptyList();
        }

        // copy deps into workspace
        Files.createDirectories(procDir.resolve(".deps"));

        Collection<String> deps = runnerCfg.dependencies();
        for (String src : deps) {
            Path dst = Paths.get(src.replace(dependencyManager.getLocalCacheDir().toString(), procDir.resolve(".deps").toString()));
            IOUtils.copy(Paths.get(src), dst);
        }

        return deps.stream()
                .map(d -> d.replace(dependencyManager.getLocalCacheDir().toString(), DockerCommandBuilder.getWorkspaceDir() + "/" + ".deps"))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getContainerCfg(RunnerJob job) {
        Map<String, Object> cfg = job.getProcessCfg();
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
