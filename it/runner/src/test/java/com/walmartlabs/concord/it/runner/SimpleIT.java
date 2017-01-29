package com.walmartlabs.concord.it.runner;

import com.google.common.io.ByteStreams;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.Assert.assertEquals;

public class SimpleIT {

    private static final String JAVA_CMD = System.getProperty("java.home") + "/bin/java";

    @Test
    public void test() throws Exception {
        String instanceId = UUID.randomUUID().toString();

        // TODO constants
        String[] cmd = {JAVA_CMD, "-DinstanceId=" + instanceId, "-jar", getRunnerPath()};
        URL workDir = SimpleIT.class.getResource("simple");

        Process proc = new ProcessBuilder()
                .command(cmd)
                .directory(new File(workDir.toURI()))
                .start();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteStreams.copy(proc.getInputStream(), baos);
        byte[] ab = baos.toByteArray();

        // ---

        int code = proc.waitFor();
        assertEquals(0, code);

        assertEquals(1, grep(".*Hello, Concord.*", ab).size());
    }

    private static String getRunnerPath() {
        Properties props = new Properties();
        try {
            props.load(SimpleIT.class.getResourceAsStream("test.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props.getProperty("runner.path");
    }
}
