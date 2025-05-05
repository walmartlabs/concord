package com.walmartlabs.concord.plugins.ansible.v1;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.TimeProvider;
import com.walmartlabs.concord.plugins.ansible.*;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.walmartlabs.concord.sdk.ContextUtils.getMap;

@Named("ansible2")
public class RunPlaybookTask2 implements Task {

    private final ApiClientFactory apiClientFactory;
    private final ApiConfiguration apiCfg;
    private final SecretService secretService;
    private final DockerService dockerService;
    private final TimeProvider timeProvider;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @InjectVariable(Constants.Context.TX_ID_KEY)
    String txId;

    @InjectVariable("ansibleParams")
    Map<String, Object> defaults;

    @Inject
    public RunPlaybookTask2(ApiClientFactory apiClientFactory,
                            ApiConfiguration apiCfg,
                            SecretService secretService,
                            DockerService dockerService,
                            TimeProvider timeProvider) {

        this.apiClientFactory = apiClientFactory;
        this.apiCfg = apiCfg;
        this.secretService = secretService;
        this.dockerService = dockerService;
        this.timeProvider = timeProvider;
    }

    public void run(String dockerImageName, Map<String, Object> args, String payloadPath) throws Exception {
        Map<String, Object> argsWithDocker = new HashMap<>(args);
        argsWithDocker.put(TaskParams.DOCKER_IMAGE_KEY.getKey(), dockerImageName);
        run(this.context, Paths.get(payloadPath), argsWithDocker);
    }

    public void run(Map<String, Object> args, String payloadPath) throws Exception {
        run(this.context, Paths.get(payloadPath), args);
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> allArgs = new HashMap<>();

        Stream.of(TaskParams.values()).forEach(c ->
                addIfPresent(ctx, allArgs, c.getKey())
        );

        Path workDir = ContextUtils.getWorkDir(ctx);
        Map<String, Object> args = DeprecatedArgsProcessor.process(workDir, allArgs);

        run(ctx, workDir, args);
    }

    private void run(Context ctx, Path workDir, Map<String, Object> args) throws Exception {
        ApiClient apiClient = apiClientFactory.create(ApiClientConfiguration.builder()
                .sessionToken(ContextUtils.getSessionToken(context))
                .build());

        AnsibleSecretServiceV1 ansibleSecretService = new AnsibleSecretServiceV1(context, secretService);

        AnsibleTask task = new AnsibleTask(apiClient,
                new AnsibleAuthFactory(ansibleSecretService, timeProvider),
                ansibleSecretService,
                timeProvider);

        Map<String, Object> projectInfo = getMap(context, Constants.Request.PROJECT_INFO_KEY, null);
        String orgName = projectInfo != null ? (String) projectInfo.get("orgName") : null;

        AnsibleContext context = AnsibleContext.builder()
                .apiBaseUrl(apiClient.getBaseUrl())
                .instanceId(UUID.fromString(txId))
                .workDir(workDir)
                .tmpDir(createTmpDir(workDir))
                .defaults(defaults != null ? defaults : Collections.emptyMap())
                .args(args)
                .sessionToken(apiCfg.getSessionToken(ctx))
                .eventCorrelationId(ctx.getEventCorrelationId())
                .orgName(orgName)
                .retryCount((Integer) ctx.getVariable(Constants.Context.CURRENT_RETRY_COUNTER))
                .build();

        PlaybookProcessRunner runner = new PlaybookProcessRunnerFactory(new AnsibleDockerServiceV1(ctx, dockerService), workDir)
                .create(args);

        TaskResult.SimpleResult result = task.run(context, runner);
        result.values().forEach(ctx::setVariable);
        if (!result.ok()) {
            throw new IllegalStateException("Process finished with exit code " + result.values().get("exitCode"));
        }
    }

    private static void addIfPresent(Context src, Map<String, Object> dst, String k) {
        Object v = src.getVariable(k);
        if (v != null) {
            dst.put(k, v);
        }
    }

    private static Path createTmpDir(Path workDir) throws IOException {
        Path p = workDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        Files.createDirectories(p);
        return Files.createTempDirectory(p, "ansible");
    }
}
