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
import com.walmartlabs.concord.plugins.ansible.secrets.AnsibleSecretService;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Named("ansible")
public class AnsibleTaskV2 implements Task {

    private final Context context;
    private final ApiClient apiClient;
    private final Map<String, Object> defaults;

    @Inject
    public AnsibleTaskV2(ApiClient apiClient, Context context) {
        this.context = context;
        this.apiClient = apiClient;
        this.defaults = context.defaultVariables().toMap();
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        Map<String, Object> in = input.toMap();
        Path workDir = context.workingDirectory();

        PlaybookProcessRunner runner = new PlaybookProcessRunnerFactory(new AnsibleDockerServiceV2(context.dockerService()), workDir)
                .create(in);

        AnsibleSecretService secretService = new AnsibleSecretServiceV2(context.secretService());
        AnsibleTask task = new AnsibleTask(apiClient, new AnsibleAuthFactory(secretService), secretService);

        UUID instanceId = Objects.requireNonNull(context.processInstanceId());
        Path tmpDir = context.fileService().createTempDirectory("ansible");

        AnsibleContext ctx = AnsibleContext.builder()
                .apiBaseUrl(apiClient.getBasePath())
                .instanceId(instanceId)
                .workDir(workDir)
                .tmpDir(tmpDir)
                .defaults(defaults)
                .args(in)
                .sessionToken(context.processConfiguration().processInfo().sessionToken())
                .eventCorrelationId(context.execution().correlationId())
                .orgName(context.projectInfo() != null ? context.projectInfo().orgName() : null)
                .retryCount((Integer) context.execution().state().peekFrame(context.execution().currentThreadId()).getLocal("__retry_attempNo")) // TODO provide a SDK method for this
                .build();

        TaskResult result = task.run(ctx, runner);
        if (!result.ok()) {
            throw new IllegalStateException("Process finished with exit code " + result.values().get("exitCode"));
        }

        return result;
    }
}
