package com.walmartlabs.concord.plugins.ansible.v2;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.plugins.ansible.*;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.ApiConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Named("ansible")
public class AnsibleTaskV2 implements Task {

    private final ApiConfiguration apiConfiguration;
    private final ApiClient apiClient;
    private final FileService fileService;
    private final AnsibleDockerService dockerService;
    private final AnsibleSecretService secretService;
    private final WorkingDirectory workDir;
    private final ProcessConfiguration processConfiguration;

    @DefaultVariables
    Map<String, Object> defaults;

    @Inject
    public AnsibleTaskV2(ApiConfiguration apiConfiguration, ApiClient apiClient,
                         FileService fileService,
                         DockerService dockerService,
                         SecretService secretService,
                         WorkingDirectory workDir,
                         ProcessConfiguration processConfiguration) {

        this.apiConfiguration = apiConfiguration;
        this.apiClient = apiClient;
        this.fileService = fileService;
        this.dockerService = new DockerServiceV2(dockerService);
        this.secretService = new SecretServiceV2(secretService);
        this.workDir = workDir;
        this.processConfiguration = processConfiguration;
    }

    @Override
    public Serializable execute(TaskContext ctx) throws Exception {
        PlaybookProcessRunner runner = new PlaybookProcessRunnerFactory(dockerService, workDir.getValue())
                .create(ctx.input());

        AnsibleTask task = new AnsibleTask(apiClient,
                new AnsibleAuthFactory(secretService),
                secretService, apiConfiguration);

        UUID instanceId = Objects.requireNonNull(processConfiguration.instanceId());
        Path tmpDir = fileService.createTempDirectory("ansible");

        AnsibleContext context = AnsibleContext.builder()
                .instanceId(instanceId)
                .workDir(workDir.getValue())
                .tmpDir(tmpDir)
                .defaults(defaults)
                .args(ctx.input())
                .sessionToken(processConfiguration.processInfo().sessionToken())
                .eventCorrelationId(ctx.execution().correlationId())
                .orgName(processConfiguration.projectInfo().orgName())
                .retryCount((Integer) ctx.execution().state().peekFrame(ctx.execution().currentThreadId()).getLocal("__retry_attempNo")) // TODO provide a SDK method for this
                .build();

        TaskResult result = task.run(context, runner);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Process finished with exit code " + result.getExitCode());
        }
        return new HashMap<>(result.getResult());
    }
}
