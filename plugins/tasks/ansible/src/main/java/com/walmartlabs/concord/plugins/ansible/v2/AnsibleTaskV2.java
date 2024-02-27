package com.walmartlabs.concord.plugins.ansible.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.plugins.ansible.*;
import com.walmartlabs.concord.plugins.ansible.secrets.AnsibleSecretService;
import com.walmartlabs.concord.runtime.v2.sdk.ProjectInfo;
import com.walmartlabs.concord.runtime.v2.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.walmartlabs.concord.sdk.MapUtils.getBoolean;

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

        ProjectInfo projectInfo = context.processConfiguration().projectInfo();

        boolean debug = getBoolean(ConfigurationUtils.deepMerge(defaults, in), TaskParams.DEBUG_KEY.getKey(), false)
                || context.processConfiguration().debug();
        AnsibleContext ctx = AnsibleContext.builder()
                .apiBaseUrl(apiClient.getBaseUrl())
                .instanceId(instanceId)
                .workDir(workDir)
                .tmpDir(tmpDir)
                .defaults(defaults)
                .args(in)
                .sessionToken(context.processConfiguration().processInfo().sessionToken())
                .eventCorrelationId(context.execution().correlationId())
                .orgName(projectInfo != null ? projectInfo.orgName() : null)
                .retryCount(ContextUtils.getCurrentRetryAttemptNumber(context))
                .debug(debug)
                .build();

        TaskResult.SimpleResult result = task.run(ctx, runner);
        if (!result.ok()) {
            throw new IllegalStateException("Process finished with exit code " + result.values().get("exitCode"));
        }

        return result;
    }
}
