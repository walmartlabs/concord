package com.walmartlabs.concord.plugins.docker;

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


import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.walmartlabs.concord.plugins.docker.DockerConstants.SUCCESS_EXIT_CODE;

@Named("docker")
public class DockerTaskV2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(DockerTask.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private static final String REDIRECT_ERROR_STREAM_KEY = "redirectErrorStream";
    private static final String LOG_STD_OUT_KEY = "logOut";
    private static final String LOG_STD_ERR_KEY = "logErr";
    private static final String SAVE_STD_OUT_KEY = "saveOut";
    private static final String SAVE_STD_ERR_KEY = "saveErr";

    private final Context context;
    private final WorkingDirectory workDir;
    private final DockerService dockerService;

    @Inject
    public DockerTaskV2(Context context, WorkingDirectory workDir, DockerService dockerService) {
        this.context = context;
        this.workDir = workDir;
        this.dockerService = dockerService;
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        Path workDir = this.workDir.getValue();
        TaskParams params = new TaskParams(input);

        boolean logStdOut = input.getBoolean(LOG_STD_OUT_KEY, true);
        boolean logStdError = input.getBoolean(LOG_STD_ERR_KEY, true);
        boolean saveStdOut = input.getBoolean(SAVE_STD_OUT_KEY, false);
        boolean saveStdError = input.getBoolean(SAVE_STD_ERR_KEY, false);
        boolean redirectErrorStream = input.getBoolean(REDIRECT_ERROR_STREAM_KEY, false);

        String stdOutFilePath = null;
        if (saveStdOut) {
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
                .debug(params.debug(context.processConfiguration().debug()))
                .redirectErrorStream(redirectErrorStream)
                .stdOutFilePath(stdOutFilePath)
                .pullRetryCount(params.pullRetryCount())
                .pullRetryInterval(params.pullRetryInterval())
                .build();

        StringBuilder stdErr = new StringBuilder();
        int code = dockerService.start(spec,
                logStdOut ? line -> processLog.info("DOCKER: {}", line) : null,
                logStdError || saveStdError ? line -> {
                    if (logStdError) {
                        processLog.info("DOCKER: {}", line);
                    }
                    if (saveStdError) {
                        stdErr.append(line).append("\n");
                    }
                } : null);

        String stdOut = null;
        if (stdOutFilePath != null) {
            InputStream inputStream = Files.newInputStream(Paths.get(stdOutFilePath));
            stdOut = DockerTaskCommon.toString(inputStream);
        }

        if (code != SUCCESS_EXIT_CODE) {
            log.warn("call ['{}', '{}', '{}'] -> finished with code {}", params.image(), params.cmd(), workDir, code);
            return TaskResult.fail("Docker process finished with exit code " + code)
                    .value("stdout", stdOut)
                    .value("stderr", stdErr.toString());
        }

        log.info("call ['{}', '{}', '{}', '{}'] -> done", params.image(), params.cmd(), workDir, params.hosts());
        return TaskResult.success()
                .value("stdout", stdOut)
                .value("stderr", stdErr.toString());
    }
}

