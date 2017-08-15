package com.walmartlabs.concord.it.runner;

import com.walmartlabs.concord.common.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class AbstractRunnerIT {

    private static final String JAVA_CMD = System.getProperty("java.home") + "/bin/java";

    protected static Process exec(String instanceId, Path workDir) throws IOException {
        // TODO constants
        Path idPath = workDir.resolve("_instanceId");
        Files.write(idPath, instanceId.getBytes());

        String[] cmd = {JAVA_CMD, "-DinstanceId=" + instanceId, "-jar", getRunnerPath()};
        return new ProcessBuilder()
                .command(cmd)
                .redirectErrorStream(true)
                .directory(workDir.toFile())
                .start();
    }

    protected static byte[] readLog(Process proc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(proc.getInputStream(), baos);
        return baos.toByteArray();
    }

    private static String getRunnerPath() {
        return System.getenv("IT_RUNNER_PATH");
    }
}
