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

import com.walmartlabs.concord.sdk.DockerContainerSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static com.walmartlabs.concord.plugins.docker.DockerConstants.*;

public class DockerTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(DockerTaskCommon.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private final Path workDir;
    private boolean storeStdOut;
    private boolean logStdOut;
    private boolean storeStdErr;
    private boolean logStdErr;
    private boolean redirectErrorStream;

    private final FileService fileService;
    private final DockerService dockerService;

    public DockerTaskCommon(Path workDir, FileService fileService, DockerService dockerService) {
        this.workDir = workDir;
        this.fileService = fileService;
        this.dockerService = dockerService;
    }

    public DockerTaskCommon storeStdOut(boolean storeStdOut) {
        this.storeStdOut = storeStdOut;
        return this;
    }

    public DockerTaskCommon logStdOut(boolean logStdOut) {
        this.logStdOut = logStdOut;
        return this;
    }

    public DockerTaskCommon storeStdErr(boolean storeStdErr) {
        this.storeStdErr = storeStdErr;
        return this;
    }

    public DockerTaskCommon logStdErr(boolean logStdErr) {
        this.logStdErr = logStdErr;
        return this;
    }

    public DockerTaskCommon redirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
        return this;
    }

    public Result execute(TaskParams params) throws Exception {
        String stdOutFilePath = null;
        if (storeStdOut) {
            Path logFile = fileService.createTempFile("stdout", ".log");
            stdOutFilePath = workDir.relativize(logFile).toString();
        }

        DockerContainerSpec spec = DockerContainerSpec.builder()
                .image(params.image())
                .env(stringify(params.env()))
                .envFile(getEnvFile(params))
                .entryPoint(prepareEntryPoint(params))
                .forcePull(params.forcePull())
                .options(DockerContainerSpec.Options.builder().hosts(params.hosts()).build())
                .debug(params.debug())
                .redirectErrorStream(redirectErrorStream)
                .stdOutFilePath(stdOutFilePath)
                .pullRetryCount(params.pullRetryCount())
                .pullRetryInterval(params.pullRetryInterval())
                .build();

        StringBuilder stdErr = new StringBuilder();
        int code = dockerService.start(spec,
                logStdOut ? line -> processLog.info("DOCKER: {}", line) : null,
                logStdErr || storeStdErr ? line -> {
                    if (storeStdErr) {
                        stdErr.append(line).append("\n");
                    }

                    if (logStdErr) {
                        processLog.info("DOCKER: {}", line);
                    }
                } : null);

        if (code != SUCCESS_EXIT_CODE) {
            log.warn("call ['{}', '{}', '{}'] -> finished with code {}", params.image(), params.cmd(), workDir, code);
            throw new RuntimeException("Docker process finished with with exit code " + code);
        }

        String stdOut = null;
        if (stdOutFilePath != null) {
            InputStream inputStream = Files.newInputStream(Paths.get(stdOutFilePath));
            stdOut = toString(inputStream);
        }

        log.info("call ['{}', '{}', '{}', '{}'] -> done", params.image(), params.cmd(), workDir, params.hosts());
        return new Result(stdOut, stdErr.toString());
    }

    private String getEnvFile(TaskParams params) {
        if (params.envFile() == null) {
            return null;
        }

        Path p = workDir.resolve(params.envFile());
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("'" + ENV_FILE_KEY + "' file not found: " + params.envFile());
        }
        return p.toAbsolutePath().toString();
    }

    private String prepareEntryPoint(TaskParams params) throws IOException {
        if (params.cmd() == null) {
            return null;
        }

        // create a script containing the specified "cmd"
        Path p = fileService.createTempFile("docker", ".sh");
        Path runScript = createRunScript(p, params.cmd());
        return Paths.get(VOLUME_CONTAINER_DEST).resolve(workDir.relativize(runScript))
                .toAbsolutePath()
                .toString();
    }

    private static Path createRunScript(Path file, String cmd) throws IOException {

        String script = "#!/bin/sh\n" +
                "cd " + VOLUME_CONTAINER_DEST + "\n" +
                cmd;

        Files.write(file, script.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        updateScriptPermissions(file);

        return file;
    }

    private static Map<String, String> stringify(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }

            result.put(e.getKey(), v.toString());
        }

        return result;
    }

    private static String toString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);

        byte[] ab = new byte[1024];
        int read;
        while ((read = in.read(ab)) > 0) {
            out.write(ab, 0, read);
        }

        return new String(out.toByteArray());
    }

    private static void updateScriptPermissions(Path p) throws IOException {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(p, perms);
    }
}
