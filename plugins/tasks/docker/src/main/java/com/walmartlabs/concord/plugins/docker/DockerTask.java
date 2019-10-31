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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

@Named("docker")
public class DockerTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DockerTask.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private static final int SUCCESS_EXIT_CODE = 0;
    private static final String VOLUME_CONTAINER_DEST = "/workspace";

    public static final String CMD_KEY = "cmd";
    public static final String IMAGE_KEY = "image";
    public static final String ENV_KEY = "env";
    public static final String ENV_FILE_KEY = "envFile";
    public static final String HOSTS_KEY = "hosts";
    public static final String FORCE_PULL_KEY = "forcePull";
    public static final String DEBUG_KEY = "debug";
    public static final String STDOUT_KEY = "stdout";
    public static final String STDERR_KEY = "stderr";
    public static final String PULL_RETRY_COUNT_KEY = "pullRetryCount";
    public static final String PULL_RETRY_INTERVAL_KEY = "pullRetryInterval";

    @Inject
    private DockerService dockerService;

    @InjectVariable(Constants.Context.WORK_DIR_KEY)
    private String workDir;

    @Override
    public void execute(Context ctx) throws Exception {
        // TODO validation

        String image = ContextUtils.assertString(ctx, IMAGE_KEY);
        String cmd = ContextUtils.getString(ctx, CMD_KEY, null);
        Map<String, Object> env = ContextUtils.getMap(ctx, ENV_KEY, null);
        String envFile = ContextUtils.getString(ctx, ENV_FILE_KEY);
        List<String> hosts = ContextUtils.getList(ctx, HOSTS_KEY, null);
        boolean forcePull = ContextUtils.getBoolean(ctx, FORCE_PULL_KEY, true);
        boolean debug = ContextUtils.getBoolean(ctx, DEBUG_KEY, false);
        String stdOutVar = ContextUtils.getString(ctx, STDOUT_KEY);
        String stdErrVar = ContextUtils.getString(ctx, STDERR_KEY);
        int pullRetryCount = ContextUtils.getInt(ctx, PULL_RETRY_COUNT_KEY, 3);
        long pullRetryInterval = ContextUtils.getInt(ctx, PULL_RETRY_INTERVAL_KEY, 10_000);

        Path baseDir = Paths.get(workDir);
        Path containerDir = Paths.get(VOLUME_CONTAINER_DEST);

        if (envFile != null) {
            Path p = baseDir.resolve(envFile);
            if (!Files.exists(p)) {
                throw new IllegalArgumentException("'" + ENV_FILE_KEY + "' file not found: " + envFile);
            }
            envFile = p.toAbsolutePath().toString();
        }

        String stdOutFilePath = null;
        if (stdOutVar != null) {
            Path logFile = createTmpFile(baseDir, "stdout", ".log");
            stdOutFilePath = baseDir.relativize(logFile).toString();
        }

        String entryPoint = null;
        if (cmd != null) {
            // create a script containing the specified "cmd"
            Path runScript = createRunScript(baseDir, cmd);
            entryPoint = containerDir.resolve(baseDir.relativize(runScript))
                    .toAbsolutePath()
                    .toString();
        }

        boolean isRedirectErrorStream = stdErrVar == null && stdOutVar == null;
        DockerContainerSpec spec = DockerContainerSpec.builder()
                .image(image)
                .env(stringify(env))
                .envFile(envFile)
                .entryPoint(entryPoint)
                .forcePull(forcePull)
                .options(DockerContainerSpec.Options.builder().hosts(hosts).build())
                .debug(debug)
                .redirectErrorStream(isRedirectErrorStream)
                .stdOutFilePath(stdOutFilePath)
                .pullRetryCount(pullRetryCount)
                .pullRetryInterval(pullRetryInterval)
                .build();
        boolean withInputStream = isRedirectErrorStream || stdOutVar == null;
        boolean withErrorStream = !withInputStream || stdErrVar != null;
        StringBuilder stdErr = new StringBuilder();
        int code = dockerService.start(ctx, spec,
                withInputStream ? line -> processLog.info("DOCKER: {}", line) : null,
                withErrorStream ? line -> {
                    if (stdErrVar == null) {
                        processLog.info("DOCKER: {}", line);
                    } else {
                        stdErr.append(line).append("\n");
                    }
                } : null);

        if (code != SUCCESS_EXIT_CODE) {
            log.warn("call ['{}', '{}', '{}'] -> finished with code {}", image, cmd, workDir, code);
            throw new RuntimeException("Docker process finished with with exit code " + code);
        }

        // retrieve the saved stdout value if needed
        if (stdOutVar != null) {
            InputStream inputStream = Files.newInputStream(Paths.get(stdOutFilePath));
            String stdOut = toString(inputStream);
            ctx.setVariable(stdOutVar, stdOut);
        }

        if (stdErrVar != null) {
            ctx.setVariable(stdErrVar, stdErr.toString());
        }

        log.info("call ['{}', '{}', '{}', '{}'] -> done", image, cmd, workDir, hosts);
    }

    private static Path createRunScript(Path workDir, String cmd) throws IOException {
        Path p = createTmpFile(workDir, "docker", ".sh");

        String script = "#!/bin/sh\n" +
                "cd " + VOLUME_CONTAINER_DEST + "\n" +
                cmd;

        Files.write(p, script.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        updateScriptPermissions(p);

        return p;
    }

    private static Path createTmpFile(Path workDir, String prefix, String suffix) throws IOException {
        Path tmpDir = workDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME);
        if (!Files.exists(tmpDir)) {
            Files.createDirectories(tmpDir);
        }

        return Files.createTempFile(tmpDir, prefix, suffix);
    }

    private static void updateScriptPermissions(Path p) throws IOException {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(p, perms);
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
}
