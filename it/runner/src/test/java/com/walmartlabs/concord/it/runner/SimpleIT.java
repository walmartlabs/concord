package com.walmartlabs.concord.it.runner;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.UUID;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.Assert.assertEquals;

public class SimpleIT extends AbstractRunnerIT {

    @Test
    public void test() throws Exception {
        String instanceId = UUID.randomUUID().toString();
        Process proc = exec(instanceId, Paths.get(SimpleIT.class.getResource("simple").toURI()));

        byte[] ab = readLog(proc);
        System.out.println(new String(ab));

        int code = proc.waitFor();
        assertEquals(0, code);

        assertEquals(1, grep(".*Hello, Concord.*", ab).size());
    }
}
