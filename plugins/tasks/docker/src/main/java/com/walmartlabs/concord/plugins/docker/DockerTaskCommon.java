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

import com.walmartlabs.concord.sdk.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static com.walmartlabs.concord.plugins.docker.DockerConstants.ENV_FILE_KEY;
import static com.walmartlabs.concord.plugins.docker.DockerConstants.VOLUME_CONTAINER_DEST;

public final class DockerTaskCommon {

    public static String prepareEntryPoint(Path workDir, TaskParams params) throws IOException {
        if (params.cmd() == null) {
            return null;
        }

        // create a script containing the specified "cmd"
        Path p = createTmpFile(workDir, "docker", ".sh");
        Path runScript = createRunScript(p, params.cmd());
        return Paths.get(VOLUME_CONTAINER_DEST).resolve(workDir.relativize(runScript))
                .toAbsolutePath()
                .toString();
    }

    public static Path createTmpFile(Path workDir, String prefix, String suffix) throws IOException {
        Path tmpDir = workDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME);
        if (!Files.exists(tmpDir)) {
            Files.createDirectories(tmpDir);
        }

        return Files.createTempFile(tmpDir, prefix, suffix);
    }

    public static String getEnvFile(Path workDir, TaskParams params) {
        if (params.envFile() == null) {
            return null;
        }

        Path p = workDir.resolve(params.envFile());
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("'" + ENV_FILE_KEY + "' file not found: " + params.envFile());
        }
        return p.toAbsolutePath().toString();
    }

    public static Map<String, String> stringify(Map<String, Object> m) {
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

    public static Path createRunScript(Path file, String cmd) throws IOException {
        String script = "#!/bin/sh\n" +
                "cd " + VOLUME_CONTAINER_DEST + "\n" +
                cmd;

        Files.write(file, script.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        updateScriptPermissions(file);

        return file;
    }

    public static void updateScriptPermissions(Path p) throws IOException {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(p, perms);
    }

    public static String toString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);

        byte[] ab = new byte[1024];
        int read;
        while ((read = in.read(ab)) > 0) {
            out.write(ab, 0, read);
        }

        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private DockerTaskCommon() {
    }
}
