package com.walmartlabs.concord.plugins.docker;

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

import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.plugins.docker.DockerConstants.*;

@Named("docker")
public class DockerTask implements Task {

    private static final String STDOUT_KEY = "stdout";
    private static final String STDERR_KEY = "stderr";

    private static final String[] ALL_KEYS = {
            CMD_KEY, IMAGE_KEY, ENV_KEY, ENV_FILE_KEY, HOSTS_KEY, FORCE_PULL_KEY,
            DEBUG_KEY, PULL_RETRY_COUNT_KEY, PULL_RETRY_INTERVAL_KEY, STDOUT_KEY, STDERR_KEY
    };

    @Inject
    private com.walmartlabs.concord.sdk.DockerService dockerService;

    @InjectVariable(Constants.Context.WORK_DIR_KEY)
    private String workDir;

    @Override
    public void execute(Context ctx) throws Exception {
        TaskParams params = new TaskParams(createInput(ctx));

        String stdOutVar = ContextUtils.getString(ctx, STDOUT_KEY);
        String stdErrVar = ContextUtils.getString(ctx, STDERR_KEY);

        boolean isRedirectErrorStream = stdErrVar == null && stdOutVar == null;
        boolean withInputStream = isRedirectErrorStream || stdOutVar == null;
        boolean withErrorStream = !withInputStream || stdErrVar != null;

        Result result = new DockerTaskCommon(Paths.get(workDir), this::createTmpFile,
                (spec, outCallback, errCallback) ->
                        dockerService.start(ctx, spec,
                                outCallback != null ? outCallback::onLog : null,
                                errCallback != null ? errCallback::onLog : null))
                .storeStdOut(stdOutVar != null)
                .logStdOut(withInputStream)
                .storeStdErr(stdErrVar != null)
                .logStdErr(withErrorStream)
                .redirectErrorStream(isRedirectErrorStream)
                .execute(params);

        if (stdOutVar != null) {
            ctx.setVariable(stdOutVar, result.getStdOut());
        }

        if (stdErrVar != null) {
            ctx.setVariable(stdErrVar, result.getStdErr());
        }
    }

    private static Map<String, Object> createInput(Context ctx) {
        Map<String, Object> result = new HashMap<>();
        for (String k : ALL_KEYS) {
            result.put(k, ctx.getVariable(k));
        }
        return result;
    }

    private Path createTmpFile(String prefix, String suffix) throws IOException {
        Path tmpDir = Paths.get(workDir).resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME);
        if (!Files.exists(tmpDir)) {
            Files.createDirectories(tmpDir);
        }

        return Files.createTempFile(tmpDir, prefix, suffix);
    }
}
