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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.plugins.docker.DockerConstants.*;

@Named("docker")
public class DockerTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DockerTask.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private static final String STDOUT_KEY = "stdout";
    private static final String STDERR_KEY = "stderr";

    private static final String[] ALL_KEYS = {
            CMD_KEY, IMAGE_KEY, ENV_KEY, ENV_FILE_KEY, HOSTS_KEY, FORCE_PULL_KEY,
            DEBUG_KEY, PULL_RETRY_COUNT_KEY, PULL_RETRY_INTERVAL_KEY, STDOUT_KEY, STDERR_KEY
    };

    @Inject
    private DockerService dockerService;

    @Override
    public void execute(Context ctx) throws Exception {
        Path workDir = ContextUtils.getWorkDir(ctx);
        TaskParams params = new TaskParams(createInput(ctx));

        String stdOutVar = ContextUtils.getString(ctx, STDOUT_KEY);
        String stdErrVar = ContextUtils.getString(ctx, STDERR_KEY);

        // redirect stderr to stdout
        boolean redirectErrorStream = stdErrVar == null && stdOutVar == null;

        // redirect stdout to log
        boolean logStdOut = redirectErrorStream || stdOutVar == null;

        // redirect stderr to log
        boolean logStdErr = !logStdOut || stdErrVar != null;

        // save stderr to a variable
        boolean storeStdErr = stdErrVar != null;

        String stdOutFilePath = null;
        if (stdOutVar != null) {
            Path logFile = DockerTaskCommon.createTmpFile(workDir, "stdout", ".log");
            stdOutFilePath = workDir.relativize(logFile).toString();
        }

        DockerContainerSpec spec = DockerContainerSpec.builder()
                .image(params.image())
                .env(DockerTaskCommon.stringify(params.env()))
                .envFile(DockerTaskCommon.getEnvFile(workDir, params))
                .entryPoint(DockerTaskCommon.prepareEntryPoint(workDir, params))
                .forcePull(params.forcePull())
                .options(DockerContainerSpec.Options.builder().hosts(params.hosts()).build())
                .debug(params.debug(false))
                .redirectErrorStream(redirectErrorStream)
                .stdOutFilePath(stdOutFilePath)
                .pullRetryCount(params.pullRetryCount())
                .pullRetryInterval(params.pullRetryInterval())
                .build();

        StringBuilder stdErr = new StringBuilder();
        int code = dockerService.start(ctx, spec,
                logStdOut ? line -> processLog.info("DOCKER: {}", line) : null,
                logStdErr ? line -> {
                    if (storeStdErr) {
                        stdErr.append(line).append("\n");
                    }

                    processLog.info("DOCKER: {}", line);
                } : null);

        String stdOut = null;
        if (stdOutFilePath != null) {
            InputStream inputStream = Files.newInputStream(Paths.get(stdOutFilePath));
            stdOut = DockerTaskCommon.toString(inputStream);
        }

        if (stdOutVar != null) {
            ctx.setVariable(stdOutVar, stdOut);
        }

        if (stdErrVar != null) {
            ctx.setVariable(stdErrVar, stdErr.toString());
        }

        if (code != SUCCESS_EXIT_CODE) {
            log.warn("call ['{}', '{}', '{}'] -> finished with code {}", params.image(), params.cmd(), workDir, code);
            throw new RuntimeException("Docker process finished with with exit code " + code);
        }

        log.info("call ['{}', '{}', '{}', '{}'] -> done", params.image(), params.cmd(), workDir, params.hosts());
    }

    private static Map<String, Object> createInput(Context ctx) {
        Map<String, Object> result = new HashMap<>();
        for (String k : ALL_KEYS) {
            result.put(k, ctx.getVariable(k));
        }
        return result;
    }
}
