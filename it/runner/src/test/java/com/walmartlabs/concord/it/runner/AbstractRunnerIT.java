package com.walmartlabs.concord.it.runner;

import com.walmartlabs.concord.common.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

public abstract class AbstractRunnerIT {

    private static final String JAVA_CMD = System.getProperty("java.home") + "/bin/java";

    protected static Process exec(String instanceId, File workDir) throws IOException {
        String[] cmd = {JAVA_CMD, "-DinstanceId=" + instanceId, "-jar", getRunnerPath()};
        return new ProcessBuilder()
                .command(cmd)
                .redirectErrorStream(true)
                .directory(workDir)
                .start();
    }

    protected static byte[] readLog(Process proc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(proc.getInputStream(), baos);
        return baos.toByteArray();
    }

    private static String getRunnerPath() {
        Properties props = new Properties();
        try {
            props.load(AbstractRunnerIT.class.getResourceAsStream("test.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props.getProperty("runner.path");
    }
}
