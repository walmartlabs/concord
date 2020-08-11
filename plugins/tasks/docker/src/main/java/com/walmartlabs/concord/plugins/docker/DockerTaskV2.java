package com.walmartlabs.concord.plugins.docker;

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


import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import static com.walmartlabs.concord.plugins.docker.DockerConstants.SUCCESS_EXIT_CODE;

@Named("docker")
public class DockerTaskV2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(DockerTask.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private static final String LOG_OUTPUT_KEY = "logOutput";

    private final WorkingDirectory workDir;
    private final DockerService dockerService;

    @Inject
    public DockerTaskV2(WorkingDirectory workDir, DockerService dockerService) {
        this.workDir = workDir;
        this.dockerService = dockerService;
    }

    @Override
    public Serializable execute(Variables input) throws Exception {
        Path workDir = this.workDir.getValue();
        TaskParams params = new TaskParams(input);

        // redirect stderr to stdout
        boolean logOutput = input.getBoolean(LOG_OUTPUT_KEY, true);

        Path logFile = DockerTaskCommon.createTmpFile(workDir, "stdout", ".log");
        String stdOutFilePath = workDir.relativize(logFile).toString();

        DockerContainerSpec spec = DockerContainerSpec.builder()
                .image(params.image())
                .env(DockerTaskCommon.stringify(params.env()))
                .envFile(DockerTaskCommon.getEnvFile(workDir, params))
                .entryPoint(DockerTaskCommon.prepareEntryPoint(workDir, params))
                .forcePull(params.forcePull())
                .options(DockerContainerSpec.Options.builder().hosts(params.hosts()).build())
                .debug(params.debug())
                .redirectErrorStream(logOutput)
                .stdOutFilePath(stdOutFilePath)
                .pullRetryCount(params.pullRetryCount())
                .pullRetryInterval(params.pullRetryInterval())
                .build();

        StringBuilder stdErr = new StringBuilder();
        int code = dockerService.start(spec,
                logOutput ? line -> processLog.info("DOCKER: {}", line) : null,
                logOutput ? line -> {
                    stdErr.append(line).append("\n");
                    processLog.info("DOCKER: {}", line);
                } : null);

        if (code != SUCCESS_EXIT_CODE) {
            log.warn("call ['{}', '{}', '{}'] -> finished with code {}", params.image(), params.cmd(), workDir, code);
            throw new RuntimeException("Docker process finished with with exit code " + code);
        }

        String stdOut = null;
        if (stdOutFilePath != null) {
            InputStream inputStream = Files.newInputStream(Paths.get(stdOutFilePath));
            stdOut = DockerTaskCommon.toString(inputStream);
        }

        log.info("call ['{}', '{}', '{}', '{}'] -> done", params.image(), params.cmd(), workDir, params.hosts());
        return toMap(stdOut, stdErr.toString());
    }

    private static HashMap<String, String> toMap(String stdOut, String stdErr) {
        HashMap<String, String> output = new HashMap<>();
        output.put("stdout", stdOut);
        output.put("stderr", stdErr);
        return output;
    }
}

